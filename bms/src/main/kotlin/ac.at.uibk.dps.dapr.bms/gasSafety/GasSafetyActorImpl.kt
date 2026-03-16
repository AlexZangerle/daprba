package ac.at.uibk.dps.dapr.bms.gassafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Gas safety actor implementation.
 *
 * States: MONITORING, GAS_LEAK, ACKNOWLEDGED
 */
class GasSafetyActorImpl(
  runtimeContext: ActorRuntimeContext<GasSafetyActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), GasSafetyActor {

  enum class State { MONITORING, GAS_LEAK, ACKNOWLEDGED }

  private var state: State = State.MONITORING

  // Transient context
  private var gasLeakLocation: String = "none"

  // Persistent
  private var gasActive: Boolean = false

  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var pollTimer: ScheduledFuture<*>? = null

  init {
    enterMonitoring()
    println("[GAS] Initialized in MONITORING state")
  }

  // Pub/sub emission
  private fun emitGasLeakDetected() {
    daprClient.publishEvent(
      "pubsub", "gasLeakDetected",
      mapOf("gasLeakLocation" to gasLeakLocation)
    ).subscribe()
    println("[GAS] EMITTED gasLeakDetected (location=$gasLeakLocation)")
  }

  // Polling
  private fun cancelPoll() {
    pollTimer?.cancel(false)
    pollTimer = null
  }

  private fun schedulePoll() {
    cancelPoll()
    pollTimer = scheduler.schedule({ pollGasLeak() }, 30000, TimeUnit.MILLISECONDS)
  }

  private fun pollGasLeak() {
    service.checkGasLeak { location ->
      onCheckDone(location)
    }
  }

  // State entries
  private fun enterMonitoring() {
    state = State.MONITORING
    // CSML: after polls every 30s (no immediate check on entry unlike electrical)
    schedulePoll()
    println("[GAS] -> MONITORING")
  }

  private fun enterGasLeak() {
    state = State.GAS_LEAK
    cancelPoll()
    // CSML: entry closes valve, cuts power, emits gasLeakDetected, sets gasActive=true
    service.closeGasValve(gasLeakLocation)
    service.cutPower(gasLeakLocation)
    emitGasLeakDetected()
    gasActive = true
    println("[GAS] -> GAS_LEAK (location=$gasLeakLocation)")
  }

  private fun enterAcknowledged() {
    state = State.ACKNOWLEDGED
    // CSML: entry invokes gasLeakPurged, sets gasActive=false
    gasActive = false
    service.gasLeakPurged()
    // CSML: always { transition to monitoring } — immediate transition
    enterMonitoring()
    println("[GAS] -> ACKNOWLEDGED -> MONITORING")
  }

  // Internal: check done
  private fun onCheckDone(location: String) {
    gasLeakLocation = location
    if (state == State.MONITORING && location != "none") {
      enterGasLeak()
    } else if (state == State.MONITORING) {
      schedulePoll()
    }
  }

  // External event
  override fun onGasPurged() {
    when (state) {
      State.GAS_LEAK -> enterAcknowledged()
      else -> {}
    }
  }
}
