package ac.at.uibk.dps.dapr.bms.benchmark

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
  val intervalMs = args.find { it.startsWith("--interval=") }?.substringAfter("=")?.toLong() ?: 500L
  val metricsDir = args.find { it.startsWith("--metrics=") }?.substringAfter("=") ?: "/metrics"
  val daprUrl =
    args.find { it.startsWith("--dapr-url=") }?.substringAfter("=") ?: "http://localhost:3500"

  println("  Scenario 2 — Comfort Convergence Simulation (Dapr)")
  println("  Duration:  ${durationMs / 1000}s")
  println("  Interval:  ${intervalMs}ms")
  println("  Dapr URL:  $daprUrl")

  val http = HttpClient.newHttpClient()

  val simCsv = File("$metricsDir/simulation.csv")
  simCsv.parentFile.mkdirs()
  simCsv.writeText("epoch_ns,event_type\n")

  fun publishSensor() {
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val req =
      HttpRequest.newBuilder()
        .uri(URI.create("$daprUrl/v1.0/publish/pubsub/sensorOccupancyReceived"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""{"imageData":"bench","time":$epochNanos}"""))
        .build()
    try {
      http.send(req, HttpResponse.BodyHandlers.discarding())
    } catch (e: Exception) {
      println("  [WARN] Failed to publish: ${e.message}")
    }
  }

  log("Waiting 15s for Dapr startup...")
  Thread.sleep(15000)

  log("Sending sensor events every ${intervalMs}ms...")

  val endTime = System.currentTimeMillis() + durationMs
  var sent = 0

  while (System.currentTimeMillis() < endTime) {
    publishSensor()
    sent++
    val phase = (sent - 1) % 3
    val label =
      when (phase) {
        0 -> "sensor_detect"
        1 -> "sensor_transient"
        2 -> "sensor_vacant"
        else -> "sensor"
      }
    simCsv.appendText("${epochNanos()},$label\n")
    Thread.sleep(intervalMs)
  }

  log("Done — sent $sent sensor events (${sent / 3} cycles)")
}
