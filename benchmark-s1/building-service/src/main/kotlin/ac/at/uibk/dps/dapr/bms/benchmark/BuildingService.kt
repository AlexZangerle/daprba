package ac.at.uibk.dps.dapr.bms.benchmark

import io.javalin.Javalin
import io.javalin.http.Context
import java.io.File
import java.io.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Clock
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

data class EmptyRequest(val dummy: String = "") : Serializable

data class RoomRequest(val roomId: String) : Serializable

data class HvacRequest(val mode: String, val roomId: String) : Serializable

data class ArcFaultResponse(val arcFaultLocation: String) : Serializable

data class TripCircuitBreakerRequest(val arcFaultLocation: String) : Serializable

data class GasLeakResponse(val gasLeakLocation: String) : Serializable

data class GasValveRequest(val gasLeakLocation: String) : Serializable

object ForySerializer {
  val fory: ThreadSafeFory =
    Fory.builder().withLanguage(Language.XLANG).withRefTracking(true).buildThreadSafeFory().apply {
      listOf(
          EmptyRequest::class.java,
          RoomRequest::class.java,
          HvacRequest::class.java,
          ArcFaultResponse::class.java,
          TripCircuitBreakerRequest::class.java,
          GasLeakResponse::class.java,
          GasValveRequest::class.java,
        )
        .forEach { register(it, it.simpleName) }
    }

  private val threadBuffer = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }

  fun serialize(obj: Any): ByteArray {
    val buf = threadBuffer.get().apply { writerIndex(0) }
    fory.serialize(buf, obj)
    return buf.getBytes(0, buf.writerIndex())
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> deserialize(bytes: ByteArray): T = fory.deserialize(bytes) as T
}

// ── Benchmark logging ──

data class BenchmarkEntry(val epochNs: Long, val eventType: String, val detail: String)

fun epochNanos(): Long {
  val now = Clock.System.now()
  return (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
}

object BenchmarkLog {
  private val entries = ConcurrentLinkedQueue<BenchmarkEntry>()
  private val csvFile = File("/metrics/building_service.csv")
  var gasExpectsConsumer = false
  var elecExpectsConsumer = false

  init {
    csvFile.parentFile.mkdirs()
    csvFile.writeText("epoch_ns,event_type,detail\n")
  }

  fun logEvent(eventType: String, detail: String) {
    val ts = epochNanos()
    entries.add(BenchmarkEntry(ts, eventType, detail))
    csvFile.appendText("$ts,$eventType,$detail\n")
  }

  fun reset() {
    entries.clear()
    gasExpectsConsumer = false
    elecExpectsConsumer = false
    csvFile.writeText("epoch_ns,event_type,detail\n")
  }

  fun getEntries() = entries.toList()
}

fun respondWith(ctx: Context, obj: Any) {
  ctx.contentType("application/x-fury")
  ctx.result(ForySerializer.serialize(obj))
}

fun respondEmpty(ctx: Context) {
  ctx.status(200)
  ctx.result("")
}

// ── Main ──

fun main() {
  println("Building Service — Scenario 1 (Dapr)")

  val app = Javalin.create().start(8005)

  // Gas circuit: producer poll
  app.post("/checkGasFault") { ctx ->
    BenchmarkLog.gasExpectsConsumer = true
    BenchmarkLog.logEvent("gas_poll", "checkGasFault")
    respondWith(ctx, GasLeakResponse("Zone-A"))
  }

  // Gas circuit: producer actions
  app.post("/closeGasValve") { ctx ->
    BenchmarkLog.logEvent("gas_action", "closeGasValve")
    respondEmpty(ctx)
  }

  app.post("/cutPower") { ctx ->
    BenchmarkLog.logEvent("gas_action", "cutPower")
    respondEmpty(ctx)
  }

  app.post("/gasLeakPurged") { ctx ->
    BenchmarkLog.logEvent("gas_reset", "gasLeakPurged")
    respondEmpty(ctx)
  }

  // HVAC consumer
  app.post("/setHVAC") { ctx ->
    BenchmarkLog.gasExpectsConsumer = false
    val req: HvacRequest = ForySerializer.deserialize(ctx.bodyAsBytes())
    BenchmarkLog.logEvent("gas_consumer", req.mode)
    println("[HVAC] ${req.mode}")

    respondEmpty(ctx)
  }

  // Electrical circuit: producer poll
  app.post("/checkArcFault") { ctx ->
    BenchmarkLog.elecExpectsConsumer = true
    BenchmarkLog.logEvent("elec_poll", "checkArcFault")
    respondWith(ctx, ArcFaultResponse("Panel-B"))
  }

  // Electrical circuit: producer actions
  app.post("/tripCircuitBreaker") { ctx ->
    BenchmarkLog.logEvent("elec_action", "tripCircuitBreaker")
    respondEmpty(ctx)
  }

  app.post("/electricalFaultAcknowledged") { ctx ->
    BenchmarkLog.logEvent("elec_reset", "ackElectrical")
    respondEmpty(ctx)
  }

  // Lighting consumer
  app.post("/turnOff") { ctx ->
    BenchmarkLog.elecExpectsConsumer = false
    BenchmarkLog.logEvent("elec_consumer", "turnOff")
    println("[LIGHT] off")

    respondEmpty(ctx)
  }

  app.post("/turnOn") { ctx ->
    BenchmarkLog.elecExpectsConsumer = false
    BenchmarkLog.logEvent("elec_consumer", "turnOn")
    println("[LIGHT] on")

    respondEmpty(ctx)
  }

  // Unused
  app.post("/dim") { ctx -> respondEmpty(ctx) }
  app.post("/evacuationLights") { ctx -> respondEmpty(ctx) }
  app.post("/userLevelLight") { ctx -> respondEmpty(ctx) }

  // Benchmark control
  app.post("/benchmarkReset") { ctx ->
    BenchmarkLog.reset()
    ctx.result("reset")
  }

  app.get("/benchmarkMetrics") { ctx ->
    val sb = StringBuilder("epoch_ns,event_type,detail\n")
    BenchmarkLog.getEntries().forEach {
      sb.appendLine("${it.epochNs},${it.eventType},${it.detail}")
    }
    ctx.contentType("text/csv")
    ctx.result(sb.toString())
  }

  println("  Running on :8005, CSV -> /metrics/building_service.csv")
}
