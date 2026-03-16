package ac.at.uibk.dps.dapr.bms.tempsafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Temperature safety actor implementation.
 *
 * States: MONITORING, HIGH_RISK
 */
class TempSafetyActorImpl(
  runtimeContext: ActorRuntimeContext<TempSafetyActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), TempSafetyActor {

  enum class State { MONITORING, HIGH_RISK }

  private var state: State = State.MONITORING
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"

  // Transient context
  private var roomTemp: Double = 0.0

  // Persistent context
  private val tempThreshold: Double = 60.0

  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var pollTimer: ScheduledFuture<*>? = null

  init {
    enterMonitoring()
    println("[TEMP_SAFETY] Initialized in MONITORING state, roomId=$roomId")
  }

  // Polling
  private fun cancelPoll() {
    pollTimer?.cancel(false)
    pollTimer = null
  }

  private fun schedulePoll() {
    cancelPoll()
    pollTimer = scheduler.schedule({ pollRoomTemp() }, 30000, TimeUnit.MILLISECONDS)
  }

  private fun pollRoomTemp() {
    service.getRoomTemp(roomId) { temp ->
      onTempCheckDone(temp)
    }
  }

  // Guard
  private fun exceedsTempThreshold(): Boolean = roomTemp > tempThreshold

  // State entries
  private fun enterMonitoring() {
    state = State.MONITORING
    // CSML: after polls every 30s
    schedulePoll()
    println("[TEMP_SAFETY] -> MONITORING")
  }

  private fun enterHighRisk() {
    state = State.HIGH_RISK
    cancelPoll()
    // CSML: entry invokes highRiskTemp
    service.highRiskTemp(roomId)
    println("[TEMP_SAFETY] -> HIGH_RISK (roomTemp=${roomTemp}°C)")
  }

  // Internal: temp check done
  private fun onTempCheckDone(temp: Double) {
    roomTemp = temp
    if (state == State.MONITORING && exceedsTempThreshold()) {
      enterHighRisk()
    } else if (state == State.MONITORING) {
      schedulePoll()
    }
  }

  // External event
  override fun onTempRiskCleared() {
    when (state) {
      State.HIGH_RISK -> enterMonitoring()
      else -> {}
    }
  }
}
