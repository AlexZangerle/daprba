package ac.at.uibk.dps.dapr.bms.buildingSchedule

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration

class BuildingScheduleActorImpl(
  runtimeContext: ActorRuntimeContext<BuildingScheduleActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), BuildingScheduleActor {

  enum class State {
    EVALUATING,
    BUSINESS_HOURS,
    AFTER_HOURS,
    WEEKEND_CLOSED,
    EMERGENCY_OVERRIDE,
  }

  private var state: State = State.EVALUATING
  private var currentScheduleMode: String = ""
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  override fun initialize() {
    enterEvaluating()
  }

  private fun cancelTimer() {
    super.unregisterTimer("scheduleTimer").subscribe()
  }

  private fun scheduleTimer(delayMs: Long) {
    cancelTimer()
    super.registerActorTimer(
        "scheduleTimer",
        "onScheduleTimer",
      ByteArray(0),
        Duration.ofMillis(delayMs),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  override fun onScheduleTimer() {
    enterEvaluating()
  }

  private fun enterEvaluating() {
    state = State.EVALUATING
    cancelTimer()
    service.getScheduleMode { mode ->
      currentScheduleMode = mode
      when (mode) {
        "weekendClosed" -> enterWeekendClosed()
        "businessHours" -> enterBusinessHours()
        else -> enterAfterHours()
      }
    }
  }

  private fun enterBusinessHours() {
    state = State.BUSINESS_HOURS
    daprClient.publishEvent("pubsub", "enterBusinessHours", mapOf<String, Any>()).subscribe()
    scheduleTimer(900000)
  }

  private fun enterAfterHours() {
    state = State.AFTER_HOURS
    daprClient.publishEvent("pubsub", "enterAfterHours", mapOf<String, Any>()).subscribe()
    scheduleTimer(900000)
  }

  private fun enterWeekendClosed() {
    state = State.WEEKEND_CLOSED
    daprClient.publishEvent("pubsub", "enterWeekend", mapOf<String, Any>()).subscribe()
    scheduleTimer(7200000)
  }

  private fun enterEmergencyOverride() {
    state = State.EMERGENCY_OVERRIDE
    cancelTimer()
  }

  override fun onFireAlarm() {
    fireAlarm = true
    if (state != State.EMERGENCY_OVERRIDE) enterEmergencyOverride()
  }

  override fun onGasLeakDetected() {
    gasActive = true
    if (state != State.EMERGENCY_OVERRIDE) enterEmergencyOverride()
  }

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    if (state == State.EMERGENCY_OVERRIDE && !fireAlarm && !gasActive) enterEvaluating()
  }

  override fun onGasPurged() {
    gasActive = false
    if (state == State.EMERGENCY_OVERRIDE && !fireAlarm && !gasActive) enterEvaluating()
  }
}
