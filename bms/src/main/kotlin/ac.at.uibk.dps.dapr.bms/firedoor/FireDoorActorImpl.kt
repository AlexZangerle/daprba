package ac.at.uibk.dps.dapr.bms.firedoor

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext

/**
 * Fire door actor implementation.
 *
 * States: OPEN, CLOSED
 */
class FireDoorActorImpl(runtimeContext: ActorRuntimeContext<FireDoorActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), FireDoorActor {

  enum class State { OPEN, CLOSED }

  private var state: State = State.OPEN
  private val doorId: String = System.getenv("DOOR_ID") ?: "Door 0"

  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  init {
    enterOpen()
    println("[FIRE_DOOR] Initialized in OPEN state, doorId=$doorId")
  }

  private fun enterOpen() {
    state = State.OPEN
    service.openFireDoor(doorId)
    println("[FIRE_DOOR] -> OPEN")
  }

  private fun enterClosed() {
    state = State.CLOSED
    service.closeFireDoor(doorId)
    println("[FIRE_DOOR] -> CLOSED")
  }

  override fun onFireAlarm() {
    when (state) {
      State.OPEN -> enterClosed()
      else -> {}
    }
  }

  override fun onDisarmFireAlarm() {
    when (state) {
      State.CLOSED -> enterOpen()
      else -> {}
    }
  }
}
