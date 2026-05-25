package ac.at.uibk.dps.dapr.bms.benchmark

import io.javalin.Javalin
import io.javalin.http.Context
import java.io.File
import java.io.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

// Bindings
data class EmptyRequest(val dummy: String = "") : Serializable
data class RoomRequest(val roomId: String) : Serializable
data class HvacRequest(val mode: String, val roomId: String) : Serializable
data class OccupancyRequest(val imageData: String) : Serializable
data class OccupancyResponse(val occupancyDetected: Boolean) : Serializable

object ForySerializer {
    val fory: ThreadSafeFory =
        Fory.builder().withLanguage(Language.XLANG).withRefTracking(true)
            .buildThreadSafeFory().apply {
                listOf(
                    EmptyRequest::class.java, RoomRequest::class.java,
                    HvacRequest::class.java, OccupancyRequest::class.java,
                    OccupancyResponse::class.java,
                ).forEach { register(it, it.simpleName) }
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

data class BenchmarkEntry(val epochNs: Long, val eventType: String, val detail: String)

fun epochNanos(): Long {
    val now = Clock.System.now()
    return (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
}

object BenchmarkLog {
    private val entries = ConcurrentLinkedQueue<BenchmarkEntry>()
    private val csvFile = File("/metrics/building_service.csv")
    val callCounter = AtomicInteger(0)
    val hvacReacted = AtomicBoolean(false)
    val lightReacted = AtomicBoolean(false)

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
        callCounter.set(0)
        hvacReacted.set(false)
        lightReacted.set(false)
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

fun main() {
    println("Building Service — Scenario 2 Dapr (Comfort Convergence)")

    val app = Javalin.create().start(8005)

    // Occupancy detection: true on every 3rd call, false otherwise
    app.post("/detectOccupancy") { ctx ->
        val n = BenchmarkLog.callCounter.getAndIncrement()
        val result = (n % 3 == 0)
        if (result) {
            BenchmarkLog.hvacReacted.set(false)
            BenchmarkLog.lightReacted.set(false)
        }
        BenchmarkLog.logEvent("occupancy_detect", result.toString())
        respondWith(ctx, OccupancyResponse(result))
    }

    app.post("/setHVAC") { ctx ->
        val req: HvacRequest = ForySerializer.deserialize(ctx.bodyAsBytes())
        BenchmarkLog.logEvent("hvac_action", req.mode)
        respondEmpty(ctx)
    }

    app.post("/turnOn") { ctx ->
        BenchmarkLog.logEvent("lighting_action", "turnOn")
        respondEmpty(ctx)
    }

    app.post("/turnOff") { ctx ->
        BenchmarkLog.logEvent("lighting_action", "turnOff")
        respondEmpty(ctx)
    }
    app.post("/dim") { ctx -> respondEmpty(ctx) }
    app.post("/evacuationLights") { ctx -> respondEmpty(ctx) }
    app.post("/userLevelLight") { ctx -> respondEmpty(ctx) }
    app.post("/maintenance") { ctx -> respondEmpty(ctx) }

    // Control
    app.post("/benchmarkReset") { ctx -> BenchmarkLog.reset(); ctx.result("reset") }
    app.get("/benchmarkMetrics") { ctx ->
        val sb = StringBuilder("epoch_ns,event_type,detail\n")
        BenchmarkLog.getEntries().forEach { sb.appendLine("${it.epochNs},${it.eventType},${it.detail}") }
        ctx.contentType("text/csv")
        ctx.result(sb.toString())
    }

    println("  Running on :8005, CSV -> /metrics/building_service.csv")
}
