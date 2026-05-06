package ac.at.uibk.dps.dapr.bms.simulation

import io.javalin.Javalin
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock

fun log(msg: String) {
    val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    println("  [$time] $msg")
}

fun epochNanos(): Long {
    val now = Clock.System.now()
    return (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
}

fun main(args: Array<String>) {
    val sensors = args.find { it.startsWith("--sensors=") }?.substringAfter("=")?.toInt() ?: 5
    val intervalMs = args.find { it.startsWith("--interval=") }?.substringAfter("=")?.toLong() ?: 5000L
    val durationSec = args.find { it.startsWith("--duration=") }?.substringAfter("=")?.toLong() ?: 900L
    val cooldownMs = args.find { it.startsWith("--cooldown=") }?.substringAfter("=")?.toLong() ?: 5000L
    val startupWaitSec = args.find { it.startsWith("--startup-wait=") }?.substringAfter("=")?.toLong() ?: 10L
    val metricsDir = args.find { it.startsWith("--metrics=") }?.substringAfter("=") ?: "/metrics"
    val daprUrl = args.find { it.startsWith("--dapr-url=") }?.substringAfter("=") ?: "http://localhost:3500"

    println("  Fire Convergence Benchmark (Dapr)")
    println("  Sensors:      $sensors")
    println("  Interval:     ${intervalMs}ms")
    println("  Duration:     ${durationSec}s")
    println("  Cooldown:     ${cooldownMs}ms")
    println("  Dapr URL:     $daprUrl")
    println("  Metrics:      $metricsDir")

    val http = HttpClient.newHttpClient()
    val executor = Executors.newFixedThreadPool(minOf(sensors, 16))
    val fireAlarmDetectedNs = AtomicLong(0)

    val zones = listOf(
        "Office", "ServerRoom", "Lobby", "Hallway", "Cafeteria",
        "MeetingRoom1", "MeetingRoom2", "Storage", "Reception", "Parking",
        "Floor1", "Floor2", "Floor3", "Floor4", "Floor5",
        "Basement", "Rooftop", "Laboratory", "Workshop", "Archive",
    )

    val simCsv = File("$metricsDir/simulation.csv")
    simCsv.parentFile.mkdirs()
    simCsv.writeText("iteration,sensors,fire_started_ns,fire_detected_ns,response_time_ns,rounds\n")

    // Subscription server
    val app = Javalin.create().start(3000)

    app.get("/dapr/subscribe") { ctx ->
        ctx.contentType("application/json")
        ctx.result("""[{"pubsubname":"pubsub","topic":"fireAlarm","route":"/fireAlarm"}]""")
    }

    app.post("/fireAlarm") { ctx ->
        val receivedNs = epochNanos()
        fireAlarmDetectedNs.compareAndSet(0, receivedNs)
        ctx.status(200)
    }

    fun publish(topic: String, body: String = """{"time":${epochNanos()}}""") {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$daprUrl/v1.0/publish/pubsub/$topic"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        try {
            http.send(req, HttpResponse.BodyHandlers.discarding())
        } catch (e: Exception) {
            println("  [WARN] Failed to publish $topic: ${e.message}")
        }
    }

    fun publishSensorBurst() {
        val futures = (0 until sensors).map { i ->
            executor.submit {
                val zone = zones[i % zones.size]
                val body = """{"imageData":"","zoneId":"$zone","time":${epochNanos()}}"""
                publish("sensorFireDataReceived", body)
            }
        }
        futures.forEach { it.get() }
    }

    log("Waiting ${startupWaitSec}s for startup...")
    Thread.sleep(startupWaitSec * 1000)

    val deadlineNs = epochNanos() + (durationSec * 1_000_000_000L)
    var iteration = 0

    log("Starting benchmark...")

    while (epochNanos() < deadlineNs) {
        iteration++
        fireAlarmDetectedNs.set(0)

        val fireStartedNs = epochNanos()
        var round = 0

        while (fireAlarmDetectedNs.get() == 0L && epochNanos() < deadlineNs) {
            round++
            publishSensorBurst()
            log("  [$iteration] Round $round: sent $sensors sensors")
            Thread.sleep(intervalMs)
        }

        val detectedNs = fireAlarmDetectedNs.get()
        if (detectedNs == 0L) {
            log("  Time expired during iteration $iteration")
            break
        }

        val responseMs = (detectedNs - fireStartedNs) / 1_000_000.0
        log("  [$iteration] Detected after $round rounds (${String.format("%.1f", responseMs)}ms)")

        simCsv.appendText("$iteration,$sensors,$fireStartedNs,$detectedNs,${detectedNs - fireStartedNs},$round\n")

        log("  [$iteration] Cooldown ${cooldownMs}ms...")
        Thread.sleep(cooldownMs)

        if (epochNanos() < deadlineNs) {
            publish("disarmFireAlarm")
            Thread.sleep(2000)
            publish("disarmSmokeAlert")
            log("  [$iteration] Reset, waiting for settle...")
            Thread.sleep(cooldownMs)
        }
    }

    log("Done. $iteration iterations, $sensors sensors.")

    executor.shutdown()
    app.stop()
}
