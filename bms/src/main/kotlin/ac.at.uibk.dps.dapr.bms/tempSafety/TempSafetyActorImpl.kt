package ac.at.uibk.dps.dapr.bms.tempSafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var pollTimer: ScheduledFuture<*>? = null

  override fun initialize() {
    schedulePoll()
  }

  private fun schedulePoll() {
    pollTimer?.cancel(false)
    pollTimer =
      scheduler.schedule(
        {
          service.getRoomTemp(roomId) { temp ->
            roomTemp = temp
            if (state == State.MONITORING && roomTemp > tempThreshold) enterHighRisk()
            else if (state == State.MONITORING) schedulePoll()
          }
        },
        30000,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun enterHighRisk() {
    state = State.HIGH_RISK
    pollTimer?.cancel(false)
    service.highRiskTemp(roomId)
  }

  override fun onTempRiskCleared() {
    if (state == State.HIGH_RISK) {
      state = State.MONITORING
      schedulePoll()
    }
  }
}
