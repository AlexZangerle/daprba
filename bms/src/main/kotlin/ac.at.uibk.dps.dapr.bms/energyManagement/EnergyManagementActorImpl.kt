package ac.at.uibk.dps.dapr.bms.energyManagement

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var pollTimer: ScheduledFuture<*>? = null

  override fun initialize() {
    enterNormal()
  }

  private fun cancelPoll() {
    pollTimer?.cancel(false)
    pollTimer = null
  }

  private fun schedulePoll() {
    cancelPoll()
    pollTimer = scheduler.schedule({ startEnergyCheck() }, 30000, TimeUnit.MILLISECONDS)
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
    daprClient.publishEvent("pubsub", "energyNormalMode", mapOf<String, Any>()).subscribe()
    startEnergyCheck()
    schedulePoll()
  }

  private fun enterPeak() {
    state = State.PEAK
    daprClient.publishEvent("pubsub", "energySavingMode", mapOf<String, Any>()).subscribe()
    schedulePoll()
  }

  private fun enterGridResponse() {
    state = State.GRID_RESPONSE
    daprClient.publishEvent("pubsub", "drasticEnergySaving", mapOf<String, Any>()).subscribe()
    schedulePoll()
  }

  private fun enterSafetyLockout() {
    state = State.SAFETY_LOCKOUT
    cancelPoll()
    daprClient.publishEvent("pubsub", "energyNormalMode", mapOf<String, Any>()).subscribe()
  }

  private fun enterGasShutdown() {
    state = State.GAS_SHUTDOWN
    cancelPoll()
    daprClient.publishEvent("pubsub", "energyShutdown", mapOf<String, Any>()).subscribe()
  }

  override fun onEnterBusinessHours() {
    currentScheduleMode = "businessHours"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      startEnergyCheck()
  }

  override fun onEnterAfterHours() {
    currentScheduleMode = "afterHours"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      startEnergyCheck()
  }

  override fun onEnterWeekend() {
    currentScheduleMode = "weekend"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      startEnergyCheck()
  }

  override fun onFireAlarm() {
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE)
      enterSafetyLockout()
  }

  override fun onGasLeakDetected() {
    if (
      state == State.NORMAL ||
        state == State.PEAK ||
        state == State.GRID_RESPONSE ||
        state == State.SAFETY_LOCKOUT
    )
      enterGasShutdown()
  }

  override fun onDisarmFireAlarm() {
    if (state == State.SAFETY_LOCKOUT) enterNormal()
  }

  override fun onGasPurged() {
    if (state == State.GAS_SHUTDOWN) enterNormal()
  }
}
