package ac.at.uibk.dps.dapr.bms.lighting

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LightingActorImpl(runtimeContext: ActorRuntimeContext<LightingActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), LightingActor {

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
  private var isEnergySaving: Boolean = false
  private var isFireAlarm: Boolean = false
  private var isGasActive: Boolean = false
  private var arcFault: Boolean = false
  private var userLightLevel: Int = 0
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var flashTimer: ScheduledFuture<*>? = null

  override fun initialize() {
    service.turnOff(roomId)
  }

  private fun cancelFlash() {
    flashTimer?.cancel(false)
    flashTimer = null
  }

  private fun enterOff() {
    state = State.OFF
    cancelFlash()
    service.turnOff(roomId)
  }

  private fun enterOn() {
    state = State.ON
    cancelFlash()
    service.turnOn(roomId)
  }

  private fun enterDim() {
    state = State.DIM
    cancelFlash()
    service.dim(roomId)
  }

  private fun enterUserLevel() {
    state = State.USER_LEVEL
    cancelFlash()
    service.turnUserLevel(roomId, userLightLevel)
  }

  private fun enterSafetyOff() {
    state = State.SAFETY_OFF
    cancelFlash()
  }

  private fun enterFlashingOn() {
    state = State.FLASHING_ON
    service.turnOn(roomId)
    flashTimer =
      scheduler.schedule(
        { if (state == State.FLASHING_ON) enterFlashingOff() },
        500,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun enterFlashingOff() {
    state = State.FLASHING_OFF
    service.turnOff(roomId)
    flashTimer =
      scheduler.schedule(
        { if (state == State.FLASHING_OFF) enterFlashingOn() },
        1000,
        TimeUnit.MILLISECONDS,
      )
  }

  override fun onOccupancyDetected() {
    if (state == State.OFF) {
      if (!isEnergySaving) enterOn() else enterDim()
    } else if (state == State.DIM && !isEnergySaving) enterOn()
  }

  override fun onOccupancyTransient() {
    if (state == State.ON) enterDim()
  }

  override fun onOccupancyVacant() {
    if (state == State.ON || state == State.DIM) enterOff()
  }

  override fun onActivateLightUserLevel(lightLevel: Int) {
    if (state == State.OFF || state == State.ON || state == State.DIM) {
      userLightLevel = lightLevel
      enterUserLevel()
    }
  }

  override fun onDeactivateLightUserLevel() {
    if (state == State.USER_LEVEL) enterOff()
  }

  override fun onActivateEnergySaving() {
    if (state == State.OFF || state == State.DIM) isEnergySaving = true
  }

  override fun onDeactivateEnergySaving() {
    if (state == State.OFF || state == State.DIM) isEnergySaving = false
  }

  override fun onFireAlarm(emergencyInRoom: String) {
    isFireAlarm = true
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
      State.SAFETY_OFF -> service.evacuationLights(roomId, emergencyInRoom)
    }
  }

  override fun onGasLeakDetected() {
    isGasActive = true
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
      State.SAFETY_OFF -> service.turnOff(roomId)
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
      State.SAFETY_OFF -> service.turnOff(roomId)
    }
  }

  override fun onDisarmFireAlarm() {
    isFireAlarm = false
    if (state == State.SAFETY_OFF && !isGasActive && !isFireAlarm && !arcFault) enterOff()
  }

  override fun onGasPurged() {
    isGasActive = false
    if (state == State.SAFETY_OFF && !isGasActive && !isFireAlarm && !arcFault) enterOff()
  }

  override fun onResetElectricalFault() {
    arcFault = false
    if (state == State.SAFETY_OFF && !isGasActive && !isFireAlarm && !arcFault) enterOff()
  }

  override fun onFlashAllLights() {
    if (state == State.OFF || state == State.ON || state == State.DIM || state == State.USER_LEVEL)
      enterFlashingOn()
  }

  override fun onClearSecurityAlert() {
    if (state == State.FLASHING_ON || state == State.FLASHING_OFF) enterOff()
  }
}
