package ac.at.uibk.dps.dapr.bms.fire

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder

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

  private fun enterDetecting() {
    state = State.DETECTING
  }

  private fun enterFireAlarm() {
    state = State.FIRE_ALARM
    isFireAlarm = true
    daprClient
      .publishEvent("pubsub", "fireAlarm", mapOf("emergencyInRoom" to emergencyInRoom))
      .subscribe()
  }

  private fun enterSmokeAlert() {
    state = State.SMOKE_ALERT
    isSmokeAlert = true
    daprClient.publishEvent("pubsub", "smokeAlert", mapOf<String, Any>()).subscribe()
  }

  override fun onSensorFireDataReceived(imageData: Map<String, String>) {
    val image = imageData["imageData"] ?: ""
    val zone = imageData["zoneId"] ?: "Room 0"
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

  override fun onDisarmFireAlarm() {
    if (state == State.FIRE_ALARM) {
      isFireAlarm = false
      enterDetecting()
    }
  }

  override fun onDisarmSmokeAlert() {
    if (state == State.SMOKE_ALERT) {
      isSmokeAlert = false
      enterDetecting()
    }
  }
}
