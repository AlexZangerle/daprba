package ac.at.uibk.dps.dapr.bms.accessControl

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Access control actor implementation.
 *
 * States: INIT, LOCKED, AUTHENTICATING, CHECKING_RULES, UNLOCKED,
 *         ACCESS_DENIED, EMERGENCY_UNLOCK, FORCED_OPEN_ALARM
 */
class AccessControlActorImpl(
  runtimeContext: ActorRuntimeContext<AccessControlActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), AccessControlActor {

  enum class State {
    INIT, LOCKED, AUTHENTICATING, CHECKING_RULES, UNLOCKED,
    ACCESS_DENIED, EMERGENCY_UNLOCK, FORCED_OPEN_ALARM
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
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false

  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var timer: ScheduledFuture<*>? = null

  init {
    enterInit()
    println("[ACCESS] Initialized, doorId=$doorId")
  }

  // Pub/sub emission
  private fun emitForcedEntry() {
    daprClient.publishEvent("pubsub", "forcedEntry",
      mapOf("doorId" to doorId, "zoneId" to zoneId)).subscribe()
    println("[ACCESS] EMITTED forcedEntry (door=$doorId, zone=$zoneId)")
  }

  private fun emitAccessDeniedRule() {
    daprClient.publishEvent("pubsub", "accessDenied",
      mapOf("doorId" to doorId, "zoneId" to zoneId, "user" to userId, "reason" to "Access Rule Denied")).subscribe()
    println("[ACCESS] EMITTED accessDenied (user=$userId)")
  }

  // Timer
  private fun cancelTimer() {
    timer?.cancel(false)
    timer = null
  }

  // Guard
  private fun isSafeToExitEmergency(): Boolean = !fireAlarm && !gasActive

  // State entries
  private fun enterInit() {
    state = State.INIT
    service.getDoorRouteType(doorId) { evacRoute, zone ->
      isEvacuationRoute = evacRoute
      zoneId = zone
      enterLocked()
    }
    println("[ACCESS] -> INIT")
  }

  private fun enterLocked() {
    state = State.LOCKED
    cancelTimer()
    println("[ACCESS] -> LOCKED (zone=$zoneId, evacRoute=$isEvacuationRoute)")
  }

  private fun enterAuthenticating(cardId: String) {
    state = State.AUTHENTICATING
    service.authenticateUser(cardId) { uId, uRole, authStatus ->
      userId = uId
      userRole = uRole
      authenticationStatus = authStatus
      if (authStatus == "Success") {
        enterCheckingRules()
      } else {
        enterAccessDenied()
      }
    }
    println("[ACCESS] -> AUTHENTICATING (card=$cardId)")
  }

  private fun enterCheckingRules() {
    state = State.CHECKING_RULES
    service.checkAccessRule(userRole, zoneId, currentScheduleMode) { decision ->
      accessDecision = decision
      if (decision == "Allow") {
        enterUnlocked()
      } else {
        emitAccessDeniedRule()
        enterAccessDenied()
      }
    }
    println("[ACCESS] -> CHECKING_RULES (role=$userRole, zone=$zoneId, mode=$currentScheduleMode)")
  }

  private fun enterUnlocked() {
    state = State.UNLOCKED
    service.controlDoorLock(doorId, "unlock")
    cancelTimer()
    timer = scheduler.schedule({ onAutoRelock() }, 10000, TimeUnit.MILLISECONDS)
    println("[ACCESS] -> UNLOCKED")
  }

  private fun enterAccessDenied() {
    state = State.ACCESS_DENIED
    cancelTimer()
    timer = scheduler.schedule({ onReturnToLock() }, 3000, TimeUnit.MILLISECONDS)
    println("[ACCESS] -> ACCESS_DENIED")
  }

  private fun enterEmergencyUnlock() {
    state = State.EMERGENCY_UNLOCK
    cancelTimer()
    service.controlDoorLock(doorId, "unlock")
    println("[ACCESS] -> EMERGENCY_UNLOCK")
  }

  private fun enterForcedOpenAlarm() {
    state = State.FORCED_OPEN_ALARM
    cancelTimer()
    emitForcedEntry()
    service.controlDoorLock(doorId, "lock")
    println("[ACCESS] -> FORCED_OPEN_ALARM")
  }

  // Internal timeouts
  private fun onAutoRelock() {
    if (state == State.UNLOCKED) enterLocked()
  }

  private fun onReturnToLock() {
    if (state == State.ACCESS_DENIED) enterLocked()
  }

  // External
  override fun onAuthenticationRequest(cardId: String) {
    when (state) {
      State.LOCKED -> enterAuthenticating(cardId)
      else -> {}
    }
  }

  override fun onForceUnlockRequest() {
    when (state) {
      State.LOCKED -> enterUnlocked()
      else -> {}
    }
  }

  override fun onPhysicalTamper() {
    when (state) {
      State.LOCKED, State.UNLOCKED -> enterForcedOpenAlarm()
      else -> {}
    }
  }

  override fun onDoorForcedOpen() {
    when (state) {
      State.LOCKED, State.UNLOCKED -> enterForcedOpenAlarm()
      else -> {}
    }
  }

  override fun onLockdownZone() {
    when (state) {
      State.LOCKED -> {}
      State.UNLOCKED -> enterLocked()
      else -> {}
    }
  }

  override fun onFireAlarm() {
    fireAlarm = true
    when (state) {
      State.LOCKED, State.AUTHENTICATING, State.CHECKING_RULES,
      State.UNLOCKED, State.ACCESS_DENIED -> enterEmergencyUnlock()
      else -> {}
    }
  }

  override fun onGasLeakDetected() {
    gasActive = true
    when (state) {
      State.LOCKED -> {
        if (isEvacuationRoute) enterEmergencyUnlock()
      }
      else -> {}
    }
  }

  override fun onUnlockAllEvacuationRoutes() {
    when (state) {
      State.LOCKED -> {
        if (isEvacuationRoute) enterEmergencyUnlock()
      }
      else -> {}
    }
  }

  override fun onClearSecurityAlert() {
    when (state) {
      State.FORCED_OPEN_ALARM -> enterLocked()
      else -> {}
    }
  }

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    when (state) {
      State.EMERGENCY_UNLOCK -> {
        if (isSafeToExitEmergency()) enterLocked()
      }
      else -> {}
    }
  }

  override fun onGasPurged() {
    gasActive = false
    when (state) {
      State.EMERGENCY_UNLOCK -> {
        if (isSafeToExitEmergency()) enterLocked()
      }
      else -> {}
    }
  }

  // Schedule events
  override fun onEnterBusinessHours() {
    when (state) {
      State.LOCKED, State.EMERGENCY_UNLOCK, State.FORCED_OPEN_ALARM -> {
        currentScheduleMode = "businessHours"
        println("[ACCESS] Schedule: businessHours")
      }
      else -> {}
    }
  }

  override fun onEnterAfterHours() {
    when (state) {
      State.LOCKED, State.EMERGENCY_UNLOCK, State.FORCED_OPEN_ALARM -> {
        currentScheduleMode = "afterHours"
        println("[ACCESS] Schedule: afterHours")
      }
      else -> {}
    }
  }

  override fun onEnterWeekend() {
    when (state) {
      State.LOCKED, State.EMERGENCY_UNLOCK, State.FORCED_OPEN_ALARM -> {
        currentScheduleMode = "weekendClosed"
        println("[ACCESS] Schedule: weekendClosed")
      }
      else -> {}
    }
  }
}
