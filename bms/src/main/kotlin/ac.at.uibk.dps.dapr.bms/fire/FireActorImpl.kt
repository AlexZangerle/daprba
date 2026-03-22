package ac.at.uibk.dps.dapr.bms.fire

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder

/**
 * Fire detection actor implementation.
 *
 * States: DETECTING, FIRE_ALARM, SMOKE_ALERT
 *
 */
class FireActorImpl(runtimeContext: ActorRuntimeContext<FireActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), FireActor {

  enum class State { DETECTING, FIRE_ALARM, SMOKE_ALERT }

  private var state: State = State.DETECTING

  private var fireDetectionResult: String = "none"
  private var emergencyInRoom: String = "none"
  private var fireAlarm: Boolean = false
  private var smokeAlert: Boolean = false

  private val daprClient = DaprClientBuilder().build()

  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  init {
    println("[FIRE] Initialized in DETECTING state")
  }

  // Pub/sub emission
  private fun emitFireAlarm() {
    daprClient.publishEvent(
      "pubsub", "fireAlarm",
      mapOf("emergencyInRoom" to emergencyInRoom)
    ).subscribe()
    println("[FIRE] EMITTED fireAlarm (emergencyInRoom=$emergencyInRoom)")
  }

  private fun emitSmokeAlert() {
    daprClient.publishEvent("pubsub", "smokeAlert", mapOf<String, Any>()).subscribe()
    println("[FIRE] EMITTED smokeAlert")
  }

  // State entry
  private fun enterDetecting() {
    state = State.DETECTING
    println("[FIRE] -> DETECTING")
  }

  private fun enterFireAlarm() {
    state = State.FIRE_ALARM
    fireAlarm = true
    emitFireAlarm()
    println("[FIRE] -> FIRE_ALARM")
  }

  private fun enterSmokeAlert() {
    state = State.SMOKE_ALERT
    smokeAlert = true
    emitSmokeAlert()
    println("[FIRE] -> SMOKE_ALERT")
  }

  // Internal
  private fun onDetectionDone(result: String, room: String) {
    fireDetectionResult = result
    emergencyInRoom = room
    println("[FIRE] Detection result: $result (room: $room)")

    when (result) {
      "fire" -> enterFireAlarm()
      "smoke" -> enterSmokeAlert()
      // "none" -> stay in detecting
    }
  }

  private fun onEscalationCheckDone(result: String, room: String) {
    fireDetectionResult = result
    emergencyInRoom = room
    println("[FIRE] Escalation check result: $result (room: $room)")

    if (result == "fire") {
      smokeAlert = false
      enterFireAlarm()
    }
  }

  // External event handlers
  override fun onSensorFireDataReceived(imageData: Map<String, String>) {
    val image = imageData["imageData"] ?: ""
    val zone = imageData["zoneId"] ?: "Room 0"
    when (state) {
      State.DETECTING -> {
        service.detectFire(image, zone) { result, room ->
          onDetectionDone(result, room)
        }
      }
      State.SMOKE_ALERT -> {
        service.detectFire(image, zone) { result, room ->
          onEscalationCheckDone(result, room)
        }
      }
      State.FIRE_ALARM -> {}
    }
  }

  override fun onDisarmFireAlarm() {
    when (state) {
      State.FIRE_ALARM -> {
        fireAlarm = false
        enterDetecting()
      }
      else -> {}
    }
  }

  override fun onDisarmSmokeAlert() {
    when (state) {
      State.SMOKE_ALERT -> {
        smokeAlert = false
        enterDetecting()
      }
      else -> {}
    }
  }
}
