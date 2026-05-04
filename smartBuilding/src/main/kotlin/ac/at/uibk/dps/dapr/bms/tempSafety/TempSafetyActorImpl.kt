package ac.at.uibk.dps.dapr.bms.tempSafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.time.Duration

class TempSafetyActorImpl(
  runtimeContext: ActorRuntimeContext<TempSafetyActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), TempSafetyActor {

  enum class State {
    MONITORING,
    HIGH_RISK,
  }

  private var state: State = State.MONITORING
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"
  private var roomTemp: Double = 0.0
  private val tempThreshold: Double = 60.0
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  override fun initialize() {
    schedulePoll()
  }

  private fun schedulePoll() {
    super.registerActorTimer(
        "pollTimer",
        "onPollTimer",
      ByteArray(0),
        Duration.ofMillis(30000),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  override fun onPollTimer() {
    if (state != State.MONITORING) return
    service.getRoomTemp(roomId) { temp ->
      roomTemp = temp
      if (state == State.MONITORING && roomTemp > tempThreshold) enterHighRisk()
      else if (state == State.MONITORING) schedulePoll()
    }
  }

  private fun enterHighRisk() {
    state = State.HIGH_RISK
    super.unregisterTimer("pollTimer").subscribe()
    service.highRiskTemp(roomId)
  }

  override fun onTempRiskCleared() {
    if (state == State.HIGH_RISK) {
      state = State.MONITORING
      schedulePoll()
    }
  }
}
