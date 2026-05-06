package ac.at.uibk.dps.dapr.bms.benchmark

import io.javalin.Javalin
import io.javalin.http.Context
import java.io.File
import java.io.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

// Bindings
data class FireDetectionRequest(val imageData: String, val zoneId: String) : Serializable
data class FireDetectionResponse(val fireDetectionResult: String, val emergencyInRoom: String) : Serializable
data class FireDoorRequest(val doorId: String) : Serializable

// Fury XLANG serializer
object FuryXlang {
    val fory: ThreadSafeFory = Fory.builder()
        .withLanguage(Language.XLANG)
        .withRefTracking(true)
        .buildThreadSafeFory()
        .apply {
            listOf(
                FireDetectionRequest::class.java,
                FireDetectionResponse::class.java,
                FireDoorRequest::class.java,
            ).forEach { register(it, it.simpleName) }
        }

    private val buf = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }

    fun serialize(obj: Any): ByteArray {
        val b = buf.get().apply { writerIndex(0) }
        fory.serialize(b, obj)
        return b.getBytes(0, b.writerIndex())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> deserialize(bytes: ByteArray): T = fory.deserialize(bytes) as T
}

// Benchmark logging
data class BenchmarkEntry(val epochNs: Long, val eventType: String, val detail: String)

fun epochNanos(): Long {
    val now = Clock.System.now()
    return (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
}

object BenchmarkLog {
    private val entries = ConcurrentLinkedQueue<BenchmarkEntry>()
    private val csvFile = File("/metrics/building_service.csv")
    val detectFireCalls = AtomicInteger(0)
    val fireDetections = AtomicInteger(0)

    init {
        csvFile.parentFile.mkdirs()
        csvFile.writeText("epoch_ns,event_type,detail\n")
    }

    fun logEvent(eventType: String, detail: String) {
        val ts = epochNanos()
        entries.add(BenchmarkEntry(ts, eventType, detail))
        csvFile.appendText("$ts,$eventType,$detail\n")
    }
}

fun parseInput(ctx: Context): Any {
    val body = ctx.bodyAsBytes()
    return if (body.isEmpty()) Unit else FuryXlang.deserialize(body)
}

fun respondWith(ctx: Context, obj: Any) {
    ctx.contentType("application/x-fury")
    ctx.result(FuryXlang.serialize(obj))
}

fun respondEmpty(ctx: Context) {
    ctx.status(200)
    ctx.result("")
}

fun main() {
    println("  Building Service — Fire Convergence (Dapr)")

    val fireProbability = 0.05

    val app = Javalin.create().start(8005)

    app.post("/detectFire") { ctx ->
        val req: FireDetectionRequest = FuryXlang.deserialize(ctx.bodyAsBytes())
        BenchmarkLog.detectFireCalls.incrementAndGet()

        val result = if (ThreadLocalRandom.current().nextDouble() < fireProbability) {
            BenchmarkLog.fireDetections.incrementAndGet()
            BenchmarkLog.logEvent("fire_detected", req.zoneId)
            "fire"
        } else {
            "none"
        }

        respondWith(ctx, FireDetectionResponse(result, req.zoneId))
    }

    app.post("/closeFireDoor") { ctx ->
        val req: FireDoorRequest = FuryXlang.deserialize(ctx.bodyAsBytes())
        BenchmarkLog.logEvent("door_close", req.doorId)
        respondEmpty(ctx)
    }

    app.post("/openFireDoor") { ctx ->
        val req: FireDoorRequest = FuryXlang.deserialize(ctx.bodyAsBytes())
        BenchmarkLog.logEvent("door_open", req.doorId)
        respondEmpty(ctx)
    }

    println("  Running on port 8005")
    println("  Fire probability: ${(fireProbability * 100).toInt()}%")
    println("  CSV: /metrics/building_service.csv")
}
