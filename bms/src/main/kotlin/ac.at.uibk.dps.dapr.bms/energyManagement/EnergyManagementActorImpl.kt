package ac.at.uibk.dps.dapr.bms.energymanagement

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Energy management actor implementation.
 *
 * States: NORMAL, PEAK, GRID_RESPONSE, SAFETY_LOCKOUT, GAS_SHUTDOWN
 */
class EnergyManagementActorImpl(
  runtimeContext: ActorRuntimeContext<EnergyManagementActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), EnergyManagementActor {

  enum class State { NORMAL, PEAK, GRID_RESPONSE, SAFETY_LOCKOUT, GAS_SHUTDOWN }

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

  init {
    enterNormal()
    println("[ENERGY] Initialized in NORMAL state")
  }

  // Pub/sub emission
  private fun emitDeactivateEnergySaving() {
    daprClient.publishEvent("pubsub", "energyNormalMode", mapOf<String, Any>()).subscribe()
    println("[ENERGY] EMITTED energyNormalMode")
  }

  private fun emitActivateEnergySaving() {
    daprClient.publishEvent("pubsub", "energySavingMode", mapOf<String, Any>()).subscribe()
    println("[ENERGY] EMITTED energySavingMode")
  }

  private fun emitDrasticEnergySaving() {
    daprClient.publishEvent("pubsub", "drasticEnergySaving", mapOf<String, Any>()).subscribe()
    println("[ENERGY] EMITTED drasticEnergySaving")
  }

  private fun emitEnergyShutdown() {
    daprClient.publishEvent("pubsub", "energyShutdown", mapOf<String, Any>()).subscribe()
    println("[ENERGY] EMITTED energyShutdown")
  }

  // Timer
  private fun cancelPoll() {
    pollTimer?.cancel(false)
    pollTimer = null
  }

  private fun schedulePoll() {
    cancelPoll()
    pollTimer = scheduler.schedule({ startEnergyCheck() }, 30000, TimeUnit.MILLISECONDS)
  }

  // Guards
  private fun isGridDemandResponse(): Boolean = gridStatus == "demandResponse"
  private fun isPriceAboveMax(): Boolean = energyPrice > maxEnergyPrice

  private fun startEnergyCheck() {
    service.getEnergyPrice { price ->
      energyPrice = price
      service.checkGridStatus { status ->
        gridStatus = status
        onGridCheckDone()
      }
    }
  }

  // State entries
  private fun enterNormal() {
    state = State.NORMAL
    emitDeactivateEnergySaving()
    startEnergyCheck()
    schedulePoll()
    println("[ENERGY] -> NORMAL")
  }

  private fun enterPeak() {
    state = State.PEAK
    emitActivateEnergySaving()
    schedulePoll()
    println("[ENERGY] -> PEAK (price=$energyPrice)")
  }

  private fun enterGridResponse() {
    state = State.GRID_RESPONSE
    emitDrasticEnergySaving()
    schedulePoll()
    println("[ENERGY] -> GRID_RESPONSE")
  }

  private fun enterSafetyLockout() {
    state = State.SAFETY_LOCKOUT
    cancelPoll()
    emitDeactivateEnergySaving()
    println("[ENERGY] -> SAFETY_LOCKOUT")
  }

  private fun enterGasShutdown() {
    state = State.GAS_SHUTDOWN
    cancelPoll()
    emitEnergyShutdown()
    println("[ENERGY] -> GAS_SHUTDOWN")
  }

  // Internal
  private fun onGridCheckDone() {
    println("[ENERGY] Price: $energyPrice, Grid: $gridStatus")

    when (state) {
      State.NORMAL -> {
        when {
          isGridDemandResponse() -> enterGridResponse()
          isPriceAboveMax() -> enterPeak()
          else -> schedulePoll()
        }
      }
      State.PEAK -> {
        when {
          isGridDemandResponse() -> enterGridResponse()
          isPriceAboveMax() -> { enterPeak() }
          else -> enterNormal()
        }
      }
      State.GRID_RESPONSE -> {
        when {
          isGridDemandResponse() -> { enterGridResponse() }
          isPriceAboveMax() -> enterPeak()
          else -> enterNormal()
        }
      }
      else -> {}
    }
  }

  // Schedule events
  override fun onEnterBusinessHours() {
    currentScheduleMode = "businessHours"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE) {
      startEnergyCheck()
    }
  }

  override fun onEnterAfterHours() {
    currentScheduleMode = "afterHours"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE) {
      startEnergyCheck()
    }
  }

  override fun onEnterWeekend() {
    currentScheduleMode = "weekend"
    if (state == State.NORMAL || state == State.PEAK || state == State.GRID_RESPONSE) {
      startEnergyCheck()
    }
  }

  // Safety events
  override fun onFireAlarm() {
    when (state) {
      State.NORMAL, State.PEAK, State.GRID_RESPONSE -> enterSafetyLockout()
      else -> {}
    }
  }

  override fun onGasLeakDetected() {
    when (state) {
      State.NORMAL, State.PEAK, State.GRID_RESPONSE, State.SAFETY_LOCKOUT -> enterGasShutdown()
      else -> {}
    }
  }

  override fun onDisarmFireAlarm() {
    when (state) {
      State.SAFETY_LOCKOUT -> enterNormal()
      else -> {}
    }
  }

  override fun onGasPurged() {
    when (state) {
      State.GAS_SHUTDOWN -> enterNormal()
      else -> {}
    }
  }
}
