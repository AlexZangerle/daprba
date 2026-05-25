package ac.at.uibk.dps.dapr.bms.simulation

import io.javalin.Javalin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    val durationSec = args.find { it.startsWith("--duration=") }?.substringAfter("=")?.toLong() ?: 900L
    val startupWaitSec = args.find { it.startsWith("--startup-wait=") }?.substringAfter("=")?.toLong() ?: 15L
    val daprUrl = args.find { it.startsWith("--dapr-url=") }?.substringAfter("=") ?: "http://localhost:3500"

    println("  Full BMS Simulation (S6 — Dapr)")
    println("  Duration:     ${durationSec}s")
    println("  Dapr URL:     $daprUrl")

    val http = HttpClient.newHttpClient()
    val scheduler = Executors.newScheduledThreadPool(4)

    val gasLeakActive = AtomicBoolean(false)
    val arcFaultActive = AtomicBoolean(false)
    val fireAlarmActive = AtomicBoolean(false)
    val smokeAlertActive = AtomicBoolean(false)
    val securityAlertActive = AtomicBoolean(false)

    fun publish(topic: String, body: String) {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$daprUrl/v1.0/publish/pubsub/$topic"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        try {
            http.send(req, HttpResponse.BodyHandlers.discarding())
        } catch (e: Exception) {
            log("[WARN] Failed to publish $topic: ${e.message}")
        }
    }

    // Subscription server — Javalin on port 3000
    val app = Javalin.create().start(3000)

    app.get("/dapr/subscribe") { ctx ->
        ctx.contentType("application/json")
        ctx.result("""[
            {"pubsubname":"pubsub","topic":"gasLeakDetected","route":"/gasLeakDetected"},
            {"pubsubname":"pubsub","topic":"arcFaultDetected","route":"/arcFaultDetected"},
            {"pubsubname":"pubsub","topic":"fireAlarm","route":"/fireAlarm"},
            {"pubsubname":"pubsub","topic":"smokeAlert","route":"/smokeAlert"},
            {"pubsubname":"pubsub","topic":"flashAllLights","route":"/flashAllLights"}
        ]""")
    }

    app.post("/gasLeakDetected") { ctx ->
        if (gasLeakActive.compareAndSet(false, true)) {
            log("[EMERGENCY] Gas leak detected — will send gasPurged in 10s")
            scheduler.schedule({
                publish("gasPurged", """{"time":${epochNanos()}}""")
                gasLeakActive.set(false)
                log("[RESET] gasPurged sent")
            }, 10, TimeUnit.SECONDS)
        }
        ctx.status(200)
    }

    app.post("/arcFaultDetected") { ctx ->
        if (arcFaultActive.compareAndSet(false, true)) {
            log("[EMERGENCY] Arc fault detected — will send electricalReset in 10s")
            scheduler.schedule({
                publish("electricalReset", """{"time":${epochNanos()}}""")
                arcFaultActive.set(false)
                log("[RESET] electricalReset sent")
            }, 10, TimeUnit.SECONDS)
        }
        ctx.status(200)
    }

    app.post("/fireAlarm") { ctx ->
        if (fireAlarmActive.compareAndSet(false, true)) {
            log("[EMERGENCY] Fire alarm — will send disarmFireAlarm in 10s")
            scheduler.schedule({
                publish("disarmFireAlarm", """{"time":${epochNanos()}}""")
                fireAlarmActive.set(false)
                log("[RESET] disarmFireAlarm sent")
            }, 10, TimeUnit.SECONDS)
        }
        ctx.status(200)
    }

    app.post("/smokeAlert") { ctx ->
        if (smokeAlertActive.compareAndSet(false, true)) {
            log("[EMERGENCY] Smoke alert — will send disarmSmokeAlert in 10s")
            scheduler.schedule({
                publish("disarmSmokeAlert", """{"time":${epochNanos()}}""")
                smokeAlertActive.set(false)
                log("[RESET] disarmSmokeAlert sent")
            }, 10, TimeUnit.SECONDS)
        }
        ctx.status(200)
    }

    app.post("/flashAllLights") { ctx ->
        if (securityAlertActive.compareAndSet(false, true)) {
            log("[EMERGENCY] Security alert (flashing lights) — will send clearSecurityAlert in 10s")
            scheduler.schedule({
                publish("clearSecurityAlert", """{"time":${epochNanos()}}""")
                securityAlertActive.set(false)
                log("[RESET] clearSecurityAlert sent")
            }, 10, TimeUnit.SECONDS)
        }
        ctx.status(200)
    }

    log("Waiting ${startupWaitSec}s for Dapr startup...")
    Thread.sleep(startupWaitSec * 1000)

    log("Starting sensor loops...")

    // Occupancy sensor — every 5s
    scheduler.scheduleAtFixedRate({
        try {
            publish("sensorOccupancyReceived", """{"imageData":"data","time":${epochNanos()}}""")
            log("[SENSOR] sensorOccupancyReceived")
        } catch (e: Exception) {
            log("[WARN] occupancy publish failed: ${e.message}")
        }
    }, 0, 5, TimeUnit.SECONDS)

    // Fire sensor — every 10s
    scheduler.scheduleAtFixedRate({
        try {
            publish("sensorFireDataReceived", """{"imageData":"data","zoneId":"Office","time":${epochNanos()}}""")
            log("[SENSOR] sensorFireDataReceived")
        } catch (e: Exception) {
            log("[WARN] fire sensor publish failed: ${e.message}")
        }
    }, 2, 10, TimeUnit.SECONDS)

    // Authentication request — every 15s
    scheduler.scheduleAtFixedRate({
        try {
            publish("authenticationRequest", """{"cardId":"CARD-001","time":${epochNanos()}}""")
            log("[SENSOR] authenticationRequest")
        } catch (e: Exception) {
            log("[WARN] auth publish failed: ${e.message}")
        }
    }, 5, 15, TimeUnit.SECONDS)

    log("All loops running. Waiting ${durationSec}s...")
    Thread.sleep(durationSec * 1000)

    log("Duration complete. Shutting down.")
    scheduler.shutdown()
    scheduler.awaitTermination(10, TimeUnit.SECONDS)
    app.stop()
}
