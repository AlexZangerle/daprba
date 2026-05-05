package ac.at.uibk.dps.dapr.bms.fire

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class FireActorImpl(runtimeContext: ActorRuntimeContext<FireActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), FireActor {

  enum class State {
    DETECTING,
    FIRE_ALARM,
    SMOKE_ALERT,
  }

  private var state: State = State.DETECTING
  private var fireDetectionResult: String = "none"
  private var emergencyInRoom: String = "none"
  private var isFireAlarm: Boolean = false
  private var isSmokeAlert: Boolean = false
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val metricsRegistry = BMS.metricsRegistry

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  private fun enterDetecting() {
    state = State.DETECTING
  }

  private fun enterFireAlarm() {
    state = State.FIRE_ALARM
    isFireAlarm = true
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent(
        "pubsub",
        "fireAlarm",
        mapOf("emergencyInRoom" to emergencyInRoom, "time" to epochNanos),
      )
      .subscribe()
  }

  private fun enterSmokeAlert() {
    state = State.SMOKE_ALERT
    isSmokeAlert = true
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "smokeAlert", mapOf("time" to epochNanos)).subscribe()
  }

  override fun onSensorFireDataReceived(imageData: Map<String, Any>) {
    measureEventTime(imageData)
    val image = imageData["imageData"] as? String ?: ""
    val zone = imageData["zoneId"] as? String ?: "Room 0"
    when (state) {
      State.DETECTING -> {
        service.detectFire(image, zone) { result, room ->
          fireDetectionResult = result
          emergencyInRoom = room
          when (result) {
            "fire" -> enterFireAlarm()
            "smoke" -> enterSmokeAlert()
          }
        }
      }
      State.SMOKE_ALERT -> {
        service.detectFire(image, zone) { result, room ->
          fireDetectionResult = result
          emergencyInRoom = room
          if (result == "fire") {
            isSmokeAlert = false
            enterFireAlarm()
          }
        }
      }
      State.FIRE_ALARM -> {}
    }
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.FIRE_ALARM) {
      isFireAlarm = false
      enterDetecting()
    }
  }

  override fun onDisarmSmokeAlert(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.SMOKE_ALERT) {
      isSmokeAlert = false
      enterDetecting()
    }
  }
}
