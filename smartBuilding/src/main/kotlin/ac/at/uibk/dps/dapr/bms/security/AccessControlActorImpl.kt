package ac.at.uibk.dps.dapr.bms.security

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration

class AccessControlActorImpl(
  runtimeContext: ActorRuntimeContext<AccessControlActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), AccessControlActor {

  enum class State {
    INIT,
    LOCKED,
    AUTHENTICATING,
    CHECKING_RULES,
    UNLOCKED,
    ACCESS_DENIED,
    EMERGENCY_UNLOCK,
    FORCED_OPEN_ALARM,
  }

  private var state: State = State.INIT
  private val doorId: String = System.getenv("DOOR_ID") ?: "Door 0"
  private var zoneId: String = ""
  private var isEvacuationRoute: Boolean = false
  private var userId: String = ""
  private var userRole: String = ""
  private var authenticationStatus: String = ""
  private var currentScheduleMode: String = "unknown"
  private var accessDecision: String = ""
  private var isFireAlarm: Boolean = false
  private var isGasActive: Boolean = false
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  override fun initialize() {
    service.getDoorRouteType(doorId) { evacRoute, zone ->
      isEvacuationRoute = evacRoute
      zoneId = zone
      enterLocked()
    }
  }

  private fun cancelTimer() {
    super.unregisterActorTimer("accessTimer").block()
  }

  private fun scheduleTimer(delayMs: Long) {
    cancelTimer()
    super.registerActorTimer(
        "accessTimer",
        "onAccessTimer",
        Duration.ofMillis(delayMs),
        Duration.ofMillis(-1),
      )
      .block()
  }

  override fun onAccessTimer() {
    when (state) {
      State.UNLOCKED -> enterLocked()
      State.ACCESS_DENIED -> enterLocked()
      else -> {}
    }
  }

  private fun enterLocked() {
    state = State.LOCKED
    cancelTimer()
  }

  private fun enterAuthenticating(cardId: String) {
    state = State.AUTHENTICATING
    service.authenticateUser(cardId) { uId, uRole, authStatus ->
      userId = uId
      userRole = uRole
      authenticationStatus = authStatus
      if (authStatus == "Success") enterCheckingRules() else enterAccessDenied()
    }
  }

  private fun enterCheckingRules() {
    state = State.CHECKING_RULES
    service.checkAccessRule(userRole, zoneId, currentScheduleMode) { decision ->
      accessDecision = decision
      if (decision == "Allow") enterUnlocked()
      else {
        daprClient
          .publishEvent(
            "pubsub",
            "accessDenied",
            mapOf(
              "doorId" to doorId,
              "zoneId" to zoneId,
              "user" to userId,
              "reason" to "Access Rule Denied",
            ),
          )
          .subscribe()
        enterAccessDenied()
      }
    }
  }

  private fun enterUnlocked() {
    state = State.UNLOCKED
    cancelTimer()
    service.controlDoorLock(doorId, "unlock")
    scheduleTimer(10000)
  }

  private fun enterAccessDenied() {
    state = State.ACCESS_DENIED
    cancelTimer()
    scheduleTimer(3000)
  }

  private fun enterEmergencyUnlock() {
    state = State.EMERGENCY_UNLOCK
    cancelTimer()
    service.controlDoorLock(doorId, "unlock")
  }

  private fun enterForcedOpenAlarm() {
    state = State.FORCED_OPEN_ALARM
    cancelTimer()
    daprClient
      .publishEvent("pubsub", "forcedEntry", mapOf("doorId" to doorId, "zoneId" to zoneId))
      .subscribe()
    service.controlDoorLock(doorId, "lock")
  }

  override fun onAuthenticationRequest(cardId: String) {
    if (state == State.LOCKED) enterAuthenticating(cardId)
  }

  override fun onForceUnlockRequest() {
    if (state == State.LOCKED) enterUnlocked()
  }

  override fun onPhysicalTamper() {
    if (state == State.LOCKED || state == State.UNLOCKED) enterForcedOpenAlarm()
  }

  override fun onDoorForcedOpen() {
    if (state == State.LOCKED || state == State.UNLOCKED) enterForcedOpenAlarm()
  }

  override fun onLockdownZone() {
    if (state == State.UNLOCKED) enterLocked()
  }

  override fun onFireAlarm() {
    isFireAlarm = true
    if (
      state == State.LOCKED ||
        state == State.AUTHENTICATING ||
        state == State.CHECKING_RULES ||
        state == State.UNLOCKED ||
        state == State.ACCESS_DENIED
    )
      enterEmergencyUnlock()
  }

  override fun onGasLeakDetected() {
    isGasActive = true
    if (state == State.LOCKED && isEvacuationRoute) enterEmergencyUnlock()
  }

  override fun onUnlockAllEvacuationRoutes() {
    if (state == State.LOCKED && isEvacuationRoute) enterEmergencyUnlock()
  }

  override fun onClearSecurityAlert() {
    if (state == State.FORCED_OPEN_ALARM) enterLocked()
  }

  override fun onDisarmFireAlarm() {
    isFireAlarm = false
    if (state == State.EMERGENCY_UNLOCK && !isFireAlarm && !isGasActive) enterLocked()
  }

  override fun onGasPurged() {
    isGasActive = false
    if (state == State.EMERGENCY_UNLOCK && !isFireAlarm && !isGasActive) enterLocked()
  }

  override fun onEnterBusinessHours() {
    if (
      state == State.LOCKED || state == State.EMERGENCY_UNLOCK || state == State.FORCED_OPEN_ALARM
    )
      currentScheduleMode = "businessHours"
  }

  override fun onEnterAfterHours() {
    if (
      state == State.LOCKED || state == State.EMERGENCY_UNLOCK || state == State.FORCED_OPEN_ALARM
    )
      currentScheduleMode = "afterHours"
  }

  override fun onEnterWeekend() {
    if (
      state == State.LOCKED || state == State.EMERGENCY_UNLOCK || state == State.FORCED_OPEN_ALARM
    )
      currentScheduleMode = "weekendClosed"
  }
}
