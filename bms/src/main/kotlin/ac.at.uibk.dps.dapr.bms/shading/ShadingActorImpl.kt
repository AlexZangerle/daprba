package ac.at.uibk.dps.dapr.bms.shading

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Shading actor implementation.
 *
 * States: HALF, FULLY_OPEN, CLOSE, USER_LEVEL, EMERGENCY
 */
class ShadingActorImpl(runtimeContext: ActorRuntimeContext<ShadingActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), ShadingActor {

  enum class State { HALF, FULLY_OPEN, CLOSE, USER_LEVEL, EMERGENCY }

  private var state: State = State.HALF
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"

  private var isLocked: Boolean = false
  private var outdoorTemp: Double = 0.0
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false
  private var smokeAlert: Boolean = false
  private var arcFault: Boolean = false
  private var userBlindLevel: Int = 0

  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var tempPollTimer: ScheduledFuture<*>? = null

  init {
    enterHalf()
    println("[SHADING] Initialized in HALF state, roomId=$roomId")
  }

  // Temperature polling
  private fun cancelTempPoll() {
    tempPollTimer?.cancel(false)
    tempPollTimer = null
  }

  private fun scheduleTempPoll() {
    cancelTempPoll()
    tempPollTimer = scheduler.schedule({ pollOutdoorTemp() }, 60000, TimeUnit.MILLISECONDS)
  }

  private fun pollOutdoorTemp() {
    service.getOutdoorTemp { temp ->
      onOutdoorCheckDone(temp)
    }
  }

  // Guards
  private fun isCold(): Boolean = outdoorTemp < 15
  private fun isMedium(): Boolean = outdoorTemp >= 15 && outdoorTemp < 22
  private fun isHot(): Boolean = outdoorTemp >= 22
  private fun isNotLocked(): Boolean = !isLocked

  private fun isSafeToExitEmergency(): Boolean =
    !fireAlarm && !gasActive && !smokeAlert && !arcFault

  // State entry actions
  private fun enterHalf() {
    state = State.HALF
    cancelTempPoll()
    service.blindsHalf(roomId)
    scheduleTempPoll()
    println("[SHADING] -> HALF")
  }

  private fun enterFullyOpen() {
    state = State.FULLY_OPEN
    cancelTempPoll()
    service.blindsOpen(roomId)
    scheduleTempPoll()
    println("[SHADING] -> FULLY_OPEN")
  }

  private fun enterClose() {
    state = State.CLOSE
    cancelTempPoll()
    service.blindsClose(roomId)
    scheduleTempPoll()
    println("[SHADING] -> CLOSE")
  }

  private fun enterUserLevel() {
    state = State.USER_LEVEL
    cancelTempPoll()
    service.blindsUserLevel(roomId, userBlindLevel)
    pollOutdoorTemp()
    scheduleTempPoll()
    println("[SHADING] -> USER_LEVEL ($userBlindLevel%)")
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelTempPoll()
    service.blindsUserLevel(roomId, 100)
    println("[SHADING] -> EMERGENCY (blinds 100%)")
  }

  // Internal: outdoor temp check done
  private fun onOutdoorCheckDone(temp: Double) {
    outdoorTemp = temp
    println("[SHADING] Outdoor temp: ${temp}°C")

    when (state) {
      State.HALF -> {
        if (isNotLocked()) {
          when {
            isCold() -> enterFullyOpen()
            isHot() -> enterClose()
            else -> scheduleTempPoll()
          }
        } else scheduleTempPoll()
      }
      State.FULLY_OPEN -> {
        if (isNotLocked()) {
          when {
            isMedium() -> enterHalf()
            isHot() -> enterClose()
            else -> scheduleTempPoll()
          }
        } else scheduleTempPoll()
      }
      State.CLOSE -> {
        if (isNotLocked()) {
          when {
            isCold() -> enterFullyOpen()
            isMedium() -> enterHalf()
            else -> scheduleTempPoll()
          }
        } else scheduleTempPoll()
      }
      State.USER_LEVEL -> {
        scheduleTempPoll()
      }
      State.EMERGENCY -> {}
    }
  }

  // External event handlers
  override fun onActivateBlindsUserLevel(blindLevel: Int) {
    when (state) {
      State.HALF, State.FULLY_OPEN, State.CLOSE -> {
        userBlindLevel = blindLevel
        enterUserLevel()
      }
      else -> {}
    }
  }

  override fun onDeactivateBlindsUserLevel() {
    when (state) {
      State.USER_LEVEL -> {
        when {
          isCold() -> enterFullyOpen()
          isHot() -> enterClose()
          else -> enterHalf()
        }
      }
      else -> {}
    }
  }

  override fun onLockBlinds() {
    when (state) {
      State.HALF, State.FULLY_OPEN, State.CLOSE, State.USER_LEVEL -> {
        isLocked = true
        println("[SHADING] Blinds LOCKED")
      }
      else -> {}
    }
  }

  override fun onUnlockBlinds() {
    when (state) {
      State.HALF, State.FULLY_OPEN, State.CLOSE, State.USER_LEVEL -> {
        isLocked = false
        println("[SHADING] Blinds UNLOCKED")
      }
      else -> {}
    }
  }

  override fun onZoneActive() {
    when (state) {
      State.CLOSE -> enterHalf()
      else -> {}
    }
  }

  override fun onZoneInactive() {
    when (state) {
      State.HALF, State.FULLY_OPEN, State.USER_LEVEL -> enterClose()
      else -> {}
    }
  }

  override fun onLockdownZone() {
    when (state) {
      State.HALF, State.USER_LEVEL -> enterClose()
      else -> {}
    }
  }

  override fun onClearSecurityAlert() {
    when (state) {
      State.CLOSE -> enterHalf()
      else -> {}
    }
  }

  // Safety events
  override fun onFireAlarm() {
    fireAlarm = true
    when (state) {
      State.HALF, State.FULLY_OPEN, State.CLOSE, State.USER_LEVEL -> enterEmergency()
      else -> {}
    }
  }

  override fun onSmokeAlert() {
    smokeAlert = true
    when (state) {
      State.HALF, State.FULLY_OPEN, State.CLOSE, State.USER_LEVEL -> enterEmergency()
      else -> {}
    }
  }

  override fun onGasLeakDetected() {
    gasActive = true
    when (state) {
      State.HALF, State.FULLY_OPEN, State.CLOSE, State.USER_LEVEL -> enterEmergency()
      else -> {}
    }
  }

  override fun onArcFaultDetected() {
    arcFault = true
    when (state) {
      State.HALF, State.FULLY_OPEN, State.CLOSE, State.USER_LEVEL -> enterEmergency()
      else -> {}
    }
  }

  // Safety recovery
  override fun onDisarmFireAlarm() {
    fireAlarm = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterHalf()
  }

  override fun onGasPurged() {
    gasActive = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterHalf()
  }

  override fun onDisarmSmokeAlert() {
    smokeAlert = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterHalf()
  }

  override fun onResetElectricalFault() {
    arcFault = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterHalf()
  }
}
