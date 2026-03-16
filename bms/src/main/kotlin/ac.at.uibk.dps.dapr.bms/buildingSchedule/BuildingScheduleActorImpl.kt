package ac.at.uibk.dps.dapr.bms.buildingschedule

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Building schedule actor implementation.
 *
 * States: EVALUATING, BUSINESS_HOURS, AFTER_HOURS, WEEKEND_CLOSED, EMERGENCY_OVERRIDE
 */
class BuildingScheduleActorImpl(
  runtimeContext: ActorRuntimeContext<BuildingScheduleActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), BuildingScheduleActor {

  enum class State { EVALUATING, BUSINESS_HOURS, AFTER_HOURS, WEEKEND_CLOSED, EMERGENCY_OVERRIDE }

  private var state: State = State.EVALUATING

  private var currentScheduleMode: String = ""
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false

  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var scheduleTimer: ScheduledFuture<*>? = null

  init {
    enterEvaluating()
    println("[SCHEDULE] Initialized in EVALUATING state")
  }

  // Pub/sub emission
  private fun emitEnterBusinessHours() {
    daprClient.publishEvent("pubsub", "enterBusinessHours", mapOf<String, Any>()).subscribe()
    println("[SCHEDULE] EMITTED enterBusinessHours")
  }

  private fun emitEnterAfterHours() {
    daprClient.publishEvent("pubsub", "enterAfterHours", mapOf<String, Any>()).subscribe()
    println("[SCHEDULE] EMITTED enterAfterHours")
  }

  private fun emitEnterWeekend() {
    daprClient.publishEvent("pubsub", "enterWeekend", mapOf<String, Any>()).subscribe()
    println("[SCHEDULE] EMITTED enterWeekend")
  }

  // Timer
  private fun cancelTimer() {
    scheduleTimer?.cancel(false)
    scheduleTimer = null
  }

  private fun scheduleRecheck(delayMs: Long) {
    cancelTimer()
    scheduleTimer = scheduler.schedule({ enterEvaluating() }, delayMs, TimeUnit.MILLISECONDS)
  }

  // Guard
  private fun isSafeToExitEmergency(): Boolean = !fireAlarm && !gasActive

  // State entries
  private fun enterEvaluating() {
    state = State.EVALUATING
    cancelTimer()
    service.getScheduleMode { mode ->
      onScheduleCheckDone(mode)
    }
    println("[SCHEDULE] -> EVALUATING")
  }

  private fun enterBusinessHours() {
    state = State.BUSINESS_HOURS
    emitEnterBusinessHours()
    scheduleRecheck(900000)
    println("[SCHEDULE] -> BUSINESS_HOURS")
  }

  private fun enterAfterHours() {
    state = State.AFTER_HOURS
    emitEnterAfterHours()
    scheduleRecheck(900000)
    println("[SCHEDULE] -> AFTER_HOURS")
  }

  private fun enterWeekendClosed() {
    state = State.WEEKEND_CLOSED
    emitEnterWeekend()
    scheduleRecheck(7200000)
    println("[SCHEDULE] -> WEEKEND_CLOSED")
  }

  private fun enterEmergencyOverride() {
    state = State.EMERGENCY_OVERRIDE
    cancelTimer()
    println("[SCHEDULE] -> EMERGENCY_OVERRIDE")
  }

  // Internal
  private fun onScheduleCheckDone(mode: String) {
    currentScheduleMode = mode
    println("[SCHEDULE] Schedule mode: $mode")
    when (mode) {
      "weekendClosed" -> enterWeekendClosed()
      "businessHours" -> enterBusinessHours()
      else -> enterAfterHours()
    }
  }

  // External
  override fun onFireAlarm() {
    fireAlarm = true
    when (state) {
      State.EVALUATING, State.BUSINESS_HOURS, State.AFTER_HOURS, State.WEEKEND_CLOSED ->
        enterEmergencyOverride()
      else -> {}
    }
  }

  override fun onGasLeakDetected() {
    gasActive = true
    when (state) {
      State.EVALUATING, State.BUSINESS_HOURS, State.AFTER_HOURS, State.WEEKEND_CLOSED ->
        enterEmergencyOverride()
      else -> {}
    }
  }

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    if (state == State.EMERGENCY_OVERRIDE && isSafeToExitEmergency()) enterEvaluating()
  }

  override fun onGasPurged() {
    gasActive = false
    if (state == State.EMERGENCY_OVERRIDE && isSafeToExitEmergency()) enterEvaluating()
  }
}
