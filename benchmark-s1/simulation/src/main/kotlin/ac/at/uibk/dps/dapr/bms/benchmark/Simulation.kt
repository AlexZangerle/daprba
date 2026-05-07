package ac.at.uibk.dps.dapr.bms.benchmark

import io.javalin.Javalin
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
  val durationMs =
    args.find { it.startsWith("--duration=") }?.substringAfter("=")?.toLong() ?: 900_000L
  val settleMs = args.find { it.startsWith("--settle=") }?.substringAfter("=")?.toLong() ?: 100L
  val metricsDir = args.find { it.startsWith("--metrics=") }?.substringAfter("=") ?: "/metrics"
  val circuit = args.find { it.startsWith("--circuit=") }?.substringAfter("=") ?: "both"
  val daprUrl =
    args.find { it.startsWith("--dapr-url=") }?.substringAfter("=") ?: "http://localhost:3500"

  val doGas = circuit == "gas" || circuit == "both"
  val doElec = circuit == "electrical" || circuit == "both"

  println("  Scenario 1 — Circuit Cost Simulation (Dapr)")
  println("  Circuit:  $circuit")
  println("  Duration: ${durationMs / 1000}s")
  println("  Settle:   ${settleMs}ms")
  println("  Dapr URL: $daprUrl")

  val http = HttpClient.newHttpClient()

  val simCsv = File("$metricsDir/simulation.csv")
  simCsv.parentFile.mkdirs()
  simCsv.writeText("epoch_ns,event_type\n")

  val gasNeedsReset = AtomicBoolean(false)
  val elecNeedsReset = AtomicBoolean(false)

  // Subscription server

  val app = Javalin.create().start(3000)

  // Subscribe to topics
  app.get("/dapr/subscribe") { ctx ->
    ctx.contentType("application/json")
    ctx.result(
      """
      [
        { "pubsubname": "pubsub", "topic": "gasLeakDetected",  "route": "/gasLeakDetected"  },
        { "pubsubname": "pubsub", "topic": "arcFaultDetected", "route": "/arcFaultDetected" }
      ]
      """
        .trimIndent()
    )
  }

  // Receive Events
  app.post("/gasLeakDetected") { ctx ->
    if (doGas) {
      gasNeedsReset.set(true)
      simCsv.appendText("${epochNanos()},gasLeakDetected\n")
    }
    ctx.status(200)
  }

  app.post("/arcFaultDetected") { ctx ->
    if (doElec) {
      elecNeedsReset.set(true)
      simCsv.appendText("${epochNanos()},arcFaultDetected\n")
    }
    ctx.status(200)
  }

  fun publish(topic: String) {
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val body = """{"time":$epochNanos}"""
    val req =
      HttpRequest.newBuilder()
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

  log("Running for ${durationMs / 1000}s...")

  val endTime = System.currentTimeMillis() + durationMs
  var gasResets = 0
  var elecResets = 0

  while (System.currentTimeMillis() < endTime) {
    if (doGas && gasNeedsReset.compareAndSet(true, false)) {
      Thread.sleep(settleMs)
      publish("gasPurged")
      gasResets++
      simCsv.appendText("${epochNanos()},gasPurged\n")
    }

    if (doElec && elecNeedsReset.compareAndSet(true, false)) {
      Thread.sleep(settleMs)
      publish("electricalReset")
      elecResets++
      simCsv.appendText("${epochNanos()},electricalReset\n")
    }

    Thread.sleep(10)
  }

  log("Done — gas: $gasResets cycles, electrical: $elecResets cycles")
  println("  Metrics: $metricsDir/")

  app.stop()
}
