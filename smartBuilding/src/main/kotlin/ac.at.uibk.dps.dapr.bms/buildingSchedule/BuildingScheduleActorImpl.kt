package ac.at.uibk.dps.dapr.bms.buildingSchedule

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var scheduleTimer: ScheduledFuture<*>? = null

  override fun initialize() {
    enterEvaluating()
  }

  private fun cancelTimer() {
    scheduleTimer?.cancel(false)
    scheduleTimer = null
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
    scheduleTimer = scheduler.schedule({ enterEvaluating() }, 900000, TimeUnit.MILLISECONDS)
  }

  private fun enterAfterHours() {
    state = State.AFTER_HOURS
    daprClient.publishEvent("pubsub", "enterAfterHours", mapOf<String, Any>()).subscribe()
    scheduleTimer = scheduler.schedule({ enterEvaluating() }, 900000, TimeUnit.MILLISECONDS)
  }

  private fun enterWeekendClosed() {
    state = State.WEEKEND_CLOSED
    daprClient.publishEvent("pubsub", "enterWeekend", mapOf<String, Any>()).subscribe()
    scheduleTimer = scheduler.schedule({ enterEvaluating() }, 7200000, TimeUnit.MILLISECONDS)
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
