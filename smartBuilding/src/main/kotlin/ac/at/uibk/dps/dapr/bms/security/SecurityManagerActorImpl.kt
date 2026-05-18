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

  private val metricsRegistry = BMS.metricsRegistry

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  private fun cancelDecay() {
    super.unregisterTimer("decayTimer").subscribe()
  }

  override fun onDecayTimer() {
    if (state == State.ELEVATED_RISK) enterSecure()
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
    super.registerActorTimer(
        "decayTimer",
        "onDecayTimer",
        ByteArray(0),
        Duration.ofMillis(1800000),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  private fun enterIntrusionAlert() {
    state = State.INTRUSION_ALERT
    cancelDecay()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "flashAllLights", mapOf("time" to epochNanos)).subscribe()
    service.notifySecurity("INTRUSION ALERT:$alertDetails")
  }

  private fun enterEmergencyResponse() {
    state = State.EMERGENCY_RESPONSE
    cancelDecay()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent("pubsub", "unlockEvacuationRoutes", mapOf("time" to epochNanos))
      .subscribe()
  }

  override fun onForcedEntry(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.SECURE) {
      alertDetails = "Forced entry detected at door ${data["doorId"]} in zone ${data["zoneId"]}"
      enterIntrusionAlert()
    } else if (state == State.ELEVATED_RISK) enterIntrusionAlert()
  }

  override fun onTamperDetected(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.SECURE) {
      alertDetails = "Tamper detected on device ${data["deviceId"]} at ${data["location"]}"
      enterIntrusionAlert()
    } else if (state == State.ELEVATED_RISK) enterIntrusionAlert()
  }

  override fun onManualSecurityAlert(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.SECURE || state == State.ELEVATED_RISK) enterIntrusionAlert()
  }

  override fun onAccessDenied(data: Map<String, Any>) {
    measureEventTime(data)
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

  override fun onClearSecurityAlert(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.ELEVATED_RISK || state == State.INTRUSION_ALERT) enterSecure()
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    isFireAlarm = true
    if (state == State.SECURE || state == State.ELEVATED_RISK || state == State.INTRUSION_ALERT)
      enterEmergencyResponse()
  }

  override fun onGasLeakDetected(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = true
    if (state == State.SECURE || state == State.ELEVATED_RISK || state == State.INTRUSION_ALERT)
      enterEmergencyResponse()
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    isFireAlarm = false
    if (state == State.EMERGENCY_RESPONSE && !isFireAlarm && !isGasActive) enterSecure()
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = false
    if (state == State.EMERGENCY_RESPONSE && !isFireAlarm && !isGasActive) enterSecure()
  }
}
