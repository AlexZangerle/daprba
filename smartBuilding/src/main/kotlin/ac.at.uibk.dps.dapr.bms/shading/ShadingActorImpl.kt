package ac.at.uibk.dps.dapr.bms.shading

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ShadingActorImpl(runtimeContext: ActorRuntimeContext<ShadingActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), ShadingActor {

  enum class State {
    HALF,
    FULLY_OPEN,
    CLOSE,
    USER_LEVEL,
    EMERGENCY,
  }

  private var state: State = State.HALF
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"
  private var isLocked: Boolean = false
  private var outdoorTemp: Double = 0.0
  private var isFireAlarm: Boolean = false
  private var isGasActive: Boolean = false
  private var isSmokeAlert: Boolean = false
  private var arcFault: Boolean = false
  private var userBlindLevel: Int = 0
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var tempPollTimer: ScheduledFuture<*>? = null

  override fun initialize() {
    enterHalf()
  }

  private fun cancelPoll() {
    tempPollTimer?.cancel(false)
    tempPollTimer = null
  }

  private fun schedulePoll() {
    cancelPoll()
    tempPollTimer = scheduler.schedule({ pollOutdoor() }, 60000, TimeUnit.MILLISECONDS)
  }

  private fun pollOutdoor() {
    service.getOutdoorTemp { temp ->
      outdoorTemp = temp
      when (state) {
        State.HALF ->
          if (!isLocked)
            when {
              outdoorTemp < 15 -> enterFullyOpen()
              outdoorTemp >= 22 -> enterClose()
              else -> schedulePoll()
            }
          else schedulePoll()
        State.FULLY_OPEN ->
          if (!isLocked)
            when {
              outdoorTemp >= 15 && outdoorTemp < 22 -> enterHalf()
              outdoorTemp >= 22 -> enterClose()
              else -> schedulePoll()
            }
          else schedulePoll()
        State.CLOSE ->
          if (!isLocked)
            when {
              outdoorTemp < 15 -> enterFullyOpen()
              outdoorTemp >= 15 && outdoorTemp < 22 -> enterHalf()
              else -> schedulePoll()
            }
          else schedulePoll()
        State.USER_LEVEL -> schedulePoll()
        State.EMERGENCY -> {}
      }
    }
  }

  private fun enterHalf() {
    state = State.HALF
    cancelPoll()
    service.blindsHalf(roomId)
    schedulePoll()
  }

  private fun enterFullyOpen() {
    state = State.FULLY_OPEN
    cancelPoll()
    service.blindsOpen(roomId)
    schedulePoll()
  }

  private fun enterClose() {
    state = State.CLOSE
    cancelPoll()
    service.blindsClose(roomId)
    schedulePoll()
  }

  private fun enterUserLevel() {
    state = State.USER_LEVEL
    cancelPoll()
    service.blindsUserLevel(roomId, userBlindLevel)
    pollOutdoor()
    schedulePoll()
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelPoll()
    service.blindsUserLevel(roomId, 100)
  }

  override fun onActivateBlindsUserLevel(blindLevel: Int) {
    if (state == State.HALF || state == State.FULLY_OPEN || state == State.CLOSE) {
      userBlindLevel = blindLevel
      enterUserLevel()
    }
  }

  override fun onDeactivateBlindsUserLevel() {
    if (state == State.USER_LEVEL)
      when {
        outdoorTemp < 15 -> enterFullyOpen()
        outdoorTemp >= 22 -> enterClose()
        else -> enterHalf()
      }
  }

  override fun onLockBlinds() {
    if (state != State.EMERGENCY) isLocked = true
  }

  override fun onUnlockBlinds() {
    if (state != State.EMERGENCY) isLocked = false
  }

  override fun onZoneActive() {
    if (state == State.CLOSE) enterHalf()
  }

  override fun onZoneInactive() {
    if (state == State.HALF || state == State.FULLY_OPEN || state == State.USER_LEVEL) enterClose()
  }

  override fun onLockdownZone() {
    if (state == State.HALF || state == State.USER_LEVEL) enterClose()
  }

  override fun onClearSecurityAlert() {
    if (state == State.CLOSE) enterHalf()
  }

  override fun onFireAlarm() {
    isFireAlarm = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onSmokeAlert() {
    isSmokeAlert = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onGasLeakDetected() {
    isGasActive = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onArcFaultDetected() {
    arcFault = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onDisarmFireAlarm() {
    isFireAlarm = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterHalf()
  }

  override fun onGasPurged() {
    isGasActive = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterHalf()
  }

  override fun onDisarmSmokeAlert() {
    isSmokeAlert = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterHalf()
  }

  override fun onResetElectricalFault() {
    arcFault = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault) {
      enterHalf()
    }
  }
}
