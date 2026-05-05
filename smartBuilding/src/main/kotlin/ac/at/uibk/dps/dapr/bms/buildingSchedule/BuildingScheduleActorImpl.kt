package ac.at.uibk.dps.dapr.bms.buildingSchedule

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

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

  private val metricsRegistry = BMS.metricsRegistry

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
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "enterBusinessHours", mapOf("time" to epochNanos)).subscribe()
    scheduleTimer(900000)
  }

  private fun enterAfterHours() {
    state = State.AFTER_HOURS
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "enterAfterHours", mapOf("time" to epochNanos)).subscribe()
    scheduleTimer(900000)
  }

  private fun enterWeekendClosed() {
    state = State.WEEKEND_CLOSED
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "enterWeekend", mapOf("time" to epochNanos)).subscribe()
    scheduleTimer(7200000)
  }

  private fun enterEmergencyOverride() {
    state = State.EMERGENCY_OVERRIDE
    cancelTimer()
  }

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    fireAlarm = true
    if (state != State.EMERGENCY_OVERRIDE) enterEmergencyOverride()
  }

  override fun onGasLeakDetected(data: Map<String, Any>) {
    measureEventTime(data)
    gasActive = true
    if (state != State.EMERGENCY_OVERRIDE) enterEmergencyOverride()
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    fireAlarm = false
    if (state == State.EMERGENCY_OVERRIDE && !fireAlarm && !gasActive) enterEvaluating()
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    gasActive = false
    if (state == State.EMERGENCY_OVERRIDE && !fireAlarm && !gasActive) enterEvaluating()
  }
}
