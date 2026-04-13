package ac.at.uibk.dps.dapr.bms.firedoor

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext

class FireDoorActorImpl(runtimeContext: ActorRuntimeContext<FireDoorActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), FireDoorActor {

  enum class State {
    OPEN,
    CLOSED,
  }

  private var state: State = State.OPEN
  private val doorId: String = System.getenv("DOOR_ID") ?: "Door 0"
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  override fun initialize() {
    service.openFireDoor(doorId)
  }

  override fun onFireAlarm() {
    if (state == State.OPEN) {
      state = State.CLOSED
      service.closeFireDoor(doorId)
    }
  }

  override fun onDisarmFireAlarm() {
    if (state == State.CLOSED) {
      state = State.OPEN
      service.openFireDoor(doorId)
    }
  }
}
