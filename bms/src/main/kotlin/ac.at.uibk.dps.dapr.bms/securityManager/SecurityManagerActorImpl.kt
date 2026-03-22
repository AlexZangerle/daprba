package ac.at.uibk.dps.dapr.bms.securityManager

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Security manager actor implementation.
 *
 * States: SECURE, ELEVATED_RISK, INTRUSION_ALERT, EMERGENCY_RESPONSE
 */
class SecurityManagerActorImpl(
  runtimeContext: ActorRuntimeContext<SecurityManagerActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), SecurityManagerActor {

  enum class State { SECURE, ELEVATED_RISK, INTRUSION_ALERT, EMERGENCY_RESPONSE }

  private var state: State = State.SECURE

  private var failedAttemptsCount: Int = 0
  private var alertDetails: String = ""
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false

  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var decayTimer: ScheduledFuture<*>? = null

  init {
    println("[SECURITY] Initialized in SECURE state")
  }

  // Pub/sub emission
  private fun emitFlashAllLights() {
    daprClient.publishEvent("pubsub", "flashAllLights", mapOf<String, Any>()).subscribe()
    println("[SECURITY] EMITTED flashAllLights")
  }

  private fun emitUnlockEvacuationRoutes() {
    daprClient.publishEvent("pubsub", "unlockAllEvacuationRoutes", mapOf<String, Any>()).subscribe()
    println("[SECURITY] EMITTED unlockAllEvacuationRoutes")
  }

  // Timer
  private fun cancelDecayTimer() {
    decayTimer?.cancel(false)
    decayTimer = null
  }

  // Guard
  private fun isSafeToExitEmergency(): Boolean = !fireAlarm && !gasActive

  // State entries
  private fun enterSecure() {
    state = State.SECURE
    cancelDecayTimer()
    failedAttemptsCount = 0
    alertDetails = ""
    println("[SECURITY] -> SECURE")
  }

  private fun enterElevatedRisk() {
    state = State.ELEVATED_RISK
    service.notifySecurity(alertDetails)
    cancelDecayTimer()
    decayTimer = scheduler.schedule({ onDecayRisk() }, 1800000, TimeUnit.MILLISECONDS)
    println("[SECURITY] -> ELEVATED_RISK (attempts=$failedAttemptsCount)")
  }

  private fun enterIntrusionAlert() {
    state = State.INTRUSION_ALERT
    cancelDecayTimer()
    emitFlashAllLights()
    service.notifySecurity("INTRUSION ALERT:$alertDetails")
    println("[SECURITY] -> INTRUSION_ALERT")
  }

  private fun enterEmergencyResponse() {
    state = State.EMERGENCY_RESPONSE
    cancelDecayTimer()
    emitUnlockEvacuationRoutes()
    println("[SECURITY] -> EMERGENCY_RESPONSE")
  }

  // Internal timeout
  private fun onDecayRisk() {
    if (state == State.ELEVATED_RISK) enterSecure()
  }

  // External
  override fun onForcedEntry(data: Map<String, String>) {
    when (state) {
      State.SECURE -> {
        alertDetails = "Forced entry detected at door ${data["doorId"]} in zone ${data["zoneId"]}"
        enterIntrusionAlert()
      }
      State.ELEVATED_RISK -> enterIntrusionAlert()
      else -> {}
    }
  }

  override fun onTamperDetected(data: Map<String, String>) {
    when (state) {
      State.SECURE -> {
        alertDetails = "Tamper detected on device ${data["deviceId"]} at ${data["location"]}"
        enterIntrusionAlert()
      }
      State.ELEVATED_RISK -> enterIntrusionAlert()
      else -> {}
    }
  }

  override fun onManualSecurityAlert() {
    when (state) {
      State.SECURE, State.ELEVATED_RISK -> enterIntrusionAlert()
      else -> {}
    }
  }

  override fun onAccessDenied(data: Map<String, String>) {
    when (state) {
      State.SECURE -> {
        failedAttemptsCount++
        alertDetails = "Repeated access denied at door ${data["doorId"]}. User: ${data["user"]}"
        if (failedAttemptsCount >= 3) enterElevatedRisk()
      }
      State.ELEVATED_RISK -> {
        failedAttemptsCount++
        alertDetails = "Escalation: Further access denied at door ${data["doorId"]}. User: ${data["user"]}"
        if (failedAttemptsCount >= 5) enterIntrusionAlert()
      }
      else -> {}
    }
  }

  override fun onClearSecurityAlert() {
    when (state) {
      State.ELEVATED_RISK, State.INTRUSION_ALERT -> enterSecure()
      else -> {}
    }
  }

  override fun onFireAlarm() {
    fireAlarm = true
    when (state) {
      State.SECURE, State.ELEVATED_RISK, State.INTRUSION_ALERT -> enterEmergencyResponse()
      else -> {}
    }
  }

  override fun onGasLeakDetected() {
    gasActive = true
    when (state) {
      State.SECURE, State.ELEVATED_RISK, State.INTRUSION_ALERT -> enterEmergencyResponse()
      else -> {}
    }
  }

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    if (state == State.EMERGENCY_RESPONSE && isSafeToExitEmergency()) enterSecure()
  }

  override fun onGasPurged() {
    gasActive = false
    if (state == State.EMERGENCY_RESPONSE && isSafeToExitEmergency()) enterSecure()
  }
}
