package at.ac.uibk.dps.dapr.lighting

import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Lighting actor implementation.
 *
 * States: OFF, ON, DIM, USER_LEVEL, SAFETY_OFF, FLASHING_ON, FLASHING_OFF
 */
class LightingActorImpl(runtimeContext: ActorRuntimeContext<LightingActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), LightingActor {

  // State
  enum class State {
    OFF,
    ON,
    DIM,
    USER_LEVEL,
    SAFETY_OFF,
    FLASHING_ON,
    FLASHING_OFF,
  }

  private var state: State = State.OFF
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"

  private var energySaving: Boolean = false
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false
  private var arcFault: Boolean = false

  private var userLightLevel: Int = 0

  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var flashTimer: ScheduledFuture<*>? = null

  init {
    enterOff()
    println("[LIGHTING] Initialized in OFF state, roomId=$roomId")
  }

  private fun enterOff() {
    state = State.OFF
    cancelFlashTimer()
    service.turnOff(roomId)
    println("[LIGHTING] -> OFF")
  }

  private fun enterOn() {
    state = State.ON
    cancelFlashTimer()
    service.turnOn(roomId)
    println("[LIGHTING] -> ON")
  }

  private fun enterDim() {
    state = State.DIM
    cancelFlashTimer()
    service.dim(roomId)
    println("[LIGHTING] -> DIM")
  }

  private fun enterUserLevel() {
    state = State.USER_LEVEL
    cancelFlashTimer()
    service.turnUserLevel(roomId, userLightLevel)
    println("[LIGHTING] -> USER_LEVEL ($userLightLevel%)")
  }

  private fun enterSafetyOff() {
    state = State.SAFETY_OFF
    cancelFlashTimer()
    println("[LIGHTING] -> SAFETY_OFF")
  }

  private fun enterFlashingOn() {
    state = State.FLASHING_ON
    service.turnOn(roomId)
    scheduleFlashTimer(500) { onFlashOff() }
    println("[LIGHTING] -> FLASHING_ON")
  }

  private fun enterFlashingOff() {
    state = State.FLASHING_OFF
    service.turnOff(roomId)
    scheduleFlashTimer(1000) { onFlashOn() }
    println("[LIGHTING] -> FLASHING_OFF")
  }

  // Guards
  private fun isEnergySavingInactive(): Boolean = !energySaving

  private fun isSafeToExitEmergency(): Boolean = !gasActive && !fireAlarm && !arcFault

  // Flash timer helpers
  private fun cancelFlashTimer() {
    flashTimer?.cancel(false)
    flashTimer = null
  }

  private fun scheduleFlashTimer(delayMs: Long, action: () -> Unit) {
    cancelFlashTimer()
    flashTimer = scheduler.schedule(action, delayMs, TimeUnit.MILLISECONDS)
  }

  // Internal flash events
  private fun onFlashOn() {
    if (state == State.FLASHING_OFF) enterFlashingOn()
  }

  private fun onFlashOff() {
    if (state == State.FLASHING_ON) enterFlashingOff()
  }

  // Event handlers
  override fun onOccupancyDetected() {
    when (state) {
      State.OFF -> {
        if (isEnergySavingInactive()) enterOn() else enterDim()
      }
      State.DIM -> {
        if (isEnergySavingInactive()) enterOn()
      }
      else -> {}
    }
  }

  override fun onOccupancyTransient() {
    when (state) {
      State.ON -> enterDim()
      else -> {}
    }
  }

  override fun onOccupancyVacant() {
    when (state) {
      State.ON,
      State.DIM -> enterOff()
      else -> {}
    }
  }

  override fun onActivateLightUserLevel(lightLevel: Int) {
    when (state) {
      State.OFF,
      State.ON,
      State.DIM -> {
        userLightLevel = lightLevel
        enterUserLevel()
      }
      else -> {}
    }
  }

  override fun onDeactivateLightUserLevel() {
    when (state) {
      State.USER_LEVEL -> enterOff()
      else -> {}
    }
  }

  override fun onActivateEnergySaving() {
    when (state) {
      State.OFF,
      State.DIM -> {
        energySaving = true
        println("[LIGHTING] energySaving = true")
      }
      else -> {}
    }
  }

  override fun onDeactivateEnergySaving() {
    when (state) {
      State.OFF,
      State.DIM -> {
        energySaving = false
        println("[LIGHTING] energySaving = false")
      }
      else -> {}
    }
  }

  override fun onFireAlarm(emergencyInRoom: String) {
    fireAlarm = true
    when (state) {
      State.OFF,
      State.ON,
      State.DIM,
      State.USER_LEVEL,
      State.FLASHING_ON,
      State.FLASHING_OFF -> {
        enterSafetyOff()
        service.evacuationLights(roomId, emergencyInRoom)
      }
      State.SAFETY_OFF -> {
        service.evacuationLights(roomId, emergencyInRoom)
      }
    }
  }

  override fun onGasLeakDetected() {
    gasActive = true
    when (state) {
      State.OFF,
      State.ON,
      State.DIM,
      State.USER_LEVEL,
      State.FLASHING_ON,
      State.FLASHING_OFF -> {
        enterSafetyOff()
        service.turnOff(roomId)
      }
      State.SAFETY_OFF -> {
        service.turnOff(roomId)
      }
    }
  }

  override fun onArcFaultDetected() {
    arcFault = true
    when (state) {
      State.OFF,
      State.ON,
      State.DIM,
      State.USER_LEVEL,
      State.FLASHING_ON,
      State.FLASHING_OFF -> {
        enterSafetyOff()
        service.turnOff(roomId)
      }
      State.SAFETY_OFF -> {
        service.turnOff(roomId)
      }
    }
  }

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    when (state) {
      State.SAFETY_OFF -> {
        if (isSafeToExitEmergency()) enterOff()
      }
      else -> {}
    }
  }

  override fun onGasPurged() {
    gasActive = false
    when (state) {
      State.SAFETY_OFF -> {
        if (isSafeToExitEmergency()) enterOff()
      }
      else -> {}
    }
  }

  override fun onResetElectricalFault() {
    arcFault = false
    when (state) {
      State.SAFETY_OFF -> {
        if (isSafeToExitEmergency()) enterOff()
      }
      else -> {}
    }
  }

  override fun onFlashAllLights() {
    when (state) {
      State.OFF,
      State.ON,
      State.DIM,
      State.USER_LEVEL -> enterFlashingOn()
      else -> {}
    }
  }

  override fun onClearSecurityAlert() {
    when (state) {
      State.FLASHING_ON,
      State.FLASHING_OFF -> enterOff()
      else -> {}
    }
  }
}
