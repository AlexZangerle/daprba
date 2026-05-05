package ac.at.uibk.dps.dapr.bms.shading

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

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

  private val metricsRegistry = BMS.metricsRegistry

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun initialize() {
    enterHalf()
  }

  private fun schedulePoll() {
    cancelPoll()
    super.registerActorTimer(
        "tempPollTimer",
        "onTempPollTimer",
        ByteArray(0),
        Duration.ofMillis(60000),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  private fun cancelPoll() {
    super.unregisterTimer("tempPollTimer").subscribe()
  }

  override fun onTempPollTimer() {
    pollOutdoor()
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

  override fun onActivateBlindsUserLevel(data: Map<String, Any>) {
    measureEventTime(data)
    val blindLevel = (data["blindLevel"] as? Number)?.toInt() ?: 0
    if (state == State.HALF || state == State.FULLY_OPEN || state == State.CLOSE) {
      userBlindLevel = blindLevel
      enterUserLevel()
    }
  }

  override fun onDeactivateBlindsUserLevel(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.USER_LEVEL)
      when {
        outdoorTemp < 15 -> enterFullyOpen()
        outdoorTemp >= 22 -> enterClose()
        else -> enterHalf()
      }
  }

  override fun onLockBlinds(data: Map<String, Any>) {
    measureEventTime(data)
    if (state != State.EMERGENCY) isLocked = true
  }

  override fun onUnlockBlinds(data: Map<String, Any>) {
    measureEventTime(data)
    if (state != State.EMERGENCY) isLocked = false
  }

  override fun onZoneActive(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.CLOSE) enterHalf()
  }

  override fun onZoneInactive(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.HALF || state == State.FULLY_OPEN || state == State.USER_LEVEL) enterClose()
  }

  override fun onLockdownZone(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.HALF || state == State.USER_LEVEL) enterClose()
  }

  override fun onClearSecurityAlert(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.CLOSE) enterHalf()
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    isFireAlarm = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onSmokeAlert(data: Map<String, Any>) {
    measureEventTime(data)
    isSmokeAlert = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onGasLeakDetected(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onArcFaultDetected(data: Map<String, Any>) {
    measureEventTime(data)
    arcFault = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    isFireAlarm = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterHalf()
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterHalf()
  }

  override fun onDisarmSmokeAlert(data: Map<String, Any>) {
    measureEventTime(data)
    isSmokeAlert = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterHalf()
  }

  override fun onResetElectricalFault(data: Map<String, Any>) {
    measureEventTime(data)
    arcFault = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterHalf()
  }
}
