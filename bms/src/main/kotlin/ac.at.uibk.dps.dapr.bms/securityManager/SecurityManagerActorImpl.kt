package ac.at.uibk.dps.dapr.bms.securityManager

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SecurityManagerActorImpl(
  runtimeContext: ActorRuntimeContext<SecurityManagerActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), SecurityManagerActor {

  enum class State {
    SECURE,
    ELEVATED_RISK,
    INTRUSION_ALERT,
    EMERGENCY_RESPONSE,
  }

  private var state: State = State.SECURE
  private var failedAttemptsCount: Int = 0
  private var alertDetails: String = ""
  private var isFireAlarm: Boolean = false
  private var isGasActive: Boolean = false
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var decayTimer: ScheduledFuture<*>? = null

  private fun cancelDecay() {
    decayTimer?.cancel(false)
    decayTimer = null
  }

  private fun enterSecure() {
    state = State.SECURE
    cancelDecay()
    failedAttemptsCount = 0
    alertDetails = ""
  }

  private fun enterElevatedRisk() {
    state = State.ELEVATED_RISK
    service.notifySecurity(alertDetails)
    cancelDecay()
    decayTimer =
      scheduler.schedule(
        { if (state == State.ELEVATED_RISK) enterSecure() },
        1800000,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun enterIntrusionAlert() {
    state = State.INTRUSION_ALERT
    cancelDecay()
    daprClient.publishEvent("pubsub", "flashAllLights", mapOf<String, Any>()).subscribe()
    service.notifySecurity("INTRUSION ALERT:$alertDetails")
  }

  private fun enterEmergencyResponse() {
    state = State.EMERGENCY_RESPONSE
    cancelDecay()
    daprClient.publishEvent("pubsub", "unlockAllEvacuationRoutes", mapOf<String, Any>()).subscribe()
  }

  override fun onForcedEntry(data: Map<String, String>) {
    if (state == State.SECURE) {
      alertDetails = "Forced entry detected at door ${data["doorId"]} in zone ${data["zoneId"]}"
      enterIntrusionAlert()
    } else if (state == State.ELEVATED_RISK) enterIntrusionAlert()
  }

  override fun onTamperDetected(data: Map<String, String>) {
    if (state == State.SECURE) {
      alertDetails = "Tamper detected on device ${data["deviceId"]} at ${data["location"]}"
      enterIntrusionAlert()
    } else if (state == State.ELEVATED_RISK) enterIntrusionAlert()
  }

  override fun onManualSecurityAlert() {
    if (state == State.SECURE || state == State.ELEVATED_RISK) enterIntrusionAlert()
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
        alertDetails =
          "Escalation: Further access denied at door ${data["doorId"]}. User: ${data["user"]}"
        if (failedAttemptsCount >= 5) enterIntrusionAlert()
      }
      else -> {}
    }
  }

  override fun onClearSecurityAlert() {
    if (state == State.ELEVATED_RISK || state == State.INTRUSION_ALERT) enterSecure()
  }

  override fun onFireAlarm() {
    isFireAlarm = true
    if (state == State.SECURE || state == State.ELEVATED_RISK || state == State.INTRUSION_ALERT)
      enterEmergencyResponse()
  }

  override fun onGasLeakDetected() {
    isGasActive = true
    if (state == State.SECURE || state == State.ELEVATED_RISK || state == State.INTRUSION_ALERT)
      enterEmergencyResponse()
  }

  override fun onDisarmFireAlarm() {
    isFireAlarm = false
    if (state == State.EMERGENCY_RESPONSE && !isFireAlarm && !isGasActive) enterSecure()
  }

  override fun onGasPurged() {
    isGasActive = false
    if (state == State.EMERGENCY_RESPONSE && !isFireAlarm && !isGasActive) enterSecure()
  }
}
