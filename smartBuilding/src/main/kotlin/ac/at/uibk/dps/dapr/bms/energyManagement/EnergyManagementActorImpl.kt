package ac.at.uibk.dps.dapr.bms.energyManagement

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class EnergyManagementActorImpl(
  runtimeContext: ActorRuntimeContext<EnergyManagementActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), EnergyManagementActor {

  enum class State {
    NORMAL,
    PEAK,
    GRID_RESPONSE,
    SAFETY_LOCKOUT,
    GAS_SHUTDOWN,
  }

  private var state: State = State.NORMAL
  private var energyPrice: Double = 0.0
  private var gridStatus: String = "normal"
  private var currentScheduleMode: String = "unknown"
  private val maxEnergyPrice: Double = 45.0
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val metricsRegistry = BMS.metricsRegistry

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun initialize() {
    enterNormal()
  }

  private fun schedulePoll() {
    cancelPoll()
    super.registerActorTimer(
        "pollTimer",
        "onPollTimer",
        ByteArray(0),
        Duration.ofMillis(30000),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  private fun cancelPoll() {
    super.unregisterTimer("pollTimer").subscribe()
  }

  override fun onPollTimer() {
    startEnergyCheck()
  }

  private fun startEnergyCheck() {
    service.getEnergyPrice { price ->
      energyPrice = price
      service.checkGridStatus { status ->
        gridStatus = status
        when (state) {
          State.NORMAL ->
            when {
              gridStatus == "demandResponse" -> enterGridResponse()
              energyPrice > maxEnergyPrice -> enterPeak()
              else -> schedulePoll()
            }
          State.PEAK ->
            when {
              gridStatus == "demandResponse" -> enterGridResponse()
              energyPrice > maxEnergyPrice -> {
                state = State.PEAK
                schedulePoll()
              }
              else -> enterNormal()
            }
          State.GRID_RESPONSE ->
            when {
              gridStatus == "demandResponse" -> {
                state = State.GRID_RESPONSE
                schedulePoll()
              }
              energyPrice > maxEnergyPrice -> enterPeak()
              else -> enterNormal()
            }
          else -> {}
        }
      }
    }
  }

  private fun enterNormal() {
    state = State.NORMAL
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent("pubsub", "deactivateEnergySaving", mapOf("time" to epochNanos))
      .subscribe()
    schedulePoll()
  }

  private fun enterPeak() {
    state = State.PEAK
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "energySaving", mapOf("time" to epochNanos)).subscribe()
    schedulePoll()
  }

  private fun enterGridResponse() {
    state = State.GRID_RESPONSE
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent("pubsub", "drasticEnergySaving", mapOf("time" to epochNanos))
      .subscribe()
    schedulePoll()
  }

  private fun enterSafetyLockout() {
    state = State.SAFETY_LOCKOUT
    cancelPoll()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent("pubsub", "deactivateEnergySaving", mapOf("time" to epochNanos))
      .subscribe()
  }

  private fun enterGasShutdown() {
    state = State.GAS_SHUTDOWN
    cancelPoll()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "energyShutdown", mapOf("time" to epochNanos)).subscribe()
  }

  override fun onEnterBusinessHours(data: Map<String, Any>) {
    measureEventTime(data)
    currentScheduleMode = "businessHours"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      startEnergyCheck()
  }

  override fun onEnterAfterHours(data: Map<String, Any>) {
    measureEventTime(data)
    currentScheduleMode = "afterHours"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      startEnergyCheck()
  }

  override fun onEnterWeekend(data: Map<String, Any>) {
    measureEventTime(data)
    currentScheduleMode = "weekend"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      startEnergyCheck()
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      enterSafetyLockout()
  }

  override fun onGasLeakDetected(data: Map<String, Any>) {
    measureEventTime(data)
    if (
      state == State.NORMAL ||
        state == State.PEAK ||
        state == State.GRID_RESPONSE ||
        state == State.SAFETY_LOCKOUT
    )
      enterGasShutdown()
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.SAFETY_LOCKOUT) enterNormal()
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.GAS_SHUTDOWN) enterNormal()
  }
}
