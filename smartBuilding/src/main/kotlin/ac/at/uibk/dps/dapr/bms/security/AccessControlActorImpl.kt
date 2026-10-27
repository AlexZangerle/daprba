package ac.at.uibk.dps.dapr.bms.security

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

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

  private val metricsRegistry = BMS.metricsRegistry

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun initialize() {
    service.getDoorRouteType(doorId) { evacRoute, zone ->
      isEvacuationRoute = evacRoute
      zoneId = zone
      enterLocked()
    }
  }

  private fun cancelTimer() {
    super.unregisterTimer("accessTimer").subscribe()
  }

  private fun scheduleTimer(delayMs: Long) {
    cancelTimer()
    super.registerActorTimer(
        "accessTimer",
        "onAccessTimer",
        ByteArray(0),
        Duration.ofMillis(delayMs),
        Duration.ofMillis(-1),
      )
      .subscribe()
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
      if (decision == "allow") enterUnlocked()
      else {
        val now = Clock.System.now()
        val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
        daprClient
          .publishEvent(
            "pubsub",
            "accessDenied",
            mapOf(
              "doorId" to doorId,
              "zoneId" to zoneId,
              "user" to userId,
              "reason" to "Access Rule Denied",
              "time" to epochNanos,
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
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent(
        "pubsub",
        "forcedEntry",
        mapOf("doorId" to doorId, "zoneId" to zoneId, "time" to epochNanos),
      )
      .subscribe()
    service.controlDoorLock(doorId, "lock")
  }

  override fun onAuthenticationRequest(data: Map<String, Any>) {
    measureEventTime(data)
    val cardId = data["cardId"] as? String ?: ""
    if (state == State.LOCKED) enterAuthenticating(cardId)
  }

  override fun onForceUnlockRequest(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.LOCKED) enterUnlocked()
  }

  override fun onPhysicalTamper(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.LOCKED || state == State.UNLOCKED) enterForcedOpenAlarm()
  }

  override fun onDoorForcedOpen(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.LOCKED || state == State.UNLOCKED) enterForcedOpenAlarm()
  }

  override fun onLockdownZone(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.UNLOCKED) enterLocked()
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
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

  override fun onunlockEvacuationRoutes(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.LOCKED && isEvacuationRoute) enterEmergencyUnlock()
  }

  override fun onClearSecurityAlert(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.FORCED_OPEN_ALARM) enterLocked()
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    isFireAlarm = false
    if (state == State.EMERGENCY_UNLOCK && !isFireAlarm && !isGasActive) enterLocked()
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = false
    if (state == State.EMERGENCY_UNLOCK && !isFireAlarm && !isGasActive) enterLocked()
  }

  override fun onEnterBusinessHours(data: Map<String, Any>) {
    measureEventTime(data)
    if (
      state == State.LOCKED || state == State.EMERGENCY_UNLOCK || state == State.FORCED_OPEN_ALARM
    )
      currentScheduleMode = "businessHours"
  }

  override fun onEnterAfterHours(data: Map<String, Any>) {
    measureEventTime(data)
    if (
      state == State.LOCKED || state == State.EMERGENCY_UNLOCK || state == State.FORCED_OPEN_ALARM
    )
      currentScheduleMode = "afterHours"
  }

  override fun onEnterWeekend(data: Map<String, Any>) {
    measureEventTime(data)
    if (
      state == State.LOCKED || state == State.EMERGENCY_UNLOCK || state == State.FORCED_OPEN_ALARM
    )
      currentScheduleMode = "weekendClosed"
  }

  override fun onGasLeakDetected(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = true
  }
}
