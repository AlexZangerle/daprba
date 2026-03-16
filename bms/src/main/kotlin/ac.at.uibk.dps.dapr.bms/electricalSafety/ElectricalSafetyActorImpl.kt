package ac.at.uibk.dps.dapr.bms.electricalsafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Electrical safety actor implementation.
 *
 * States: MONITORING, ARC_FAULT, ACKNOWLEDGED
 */
class ElectricalSafetyActorImpl(
  runtimeContext: ActorRuntimeContext<ElectricalSafetyActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), ElectricalSafetyActor {

  enum class State { MONITORING, ARC_FAULT, ACKNOWLEDGED }

  private var state: State = State.MONITORING

  private var arcFaultLocation: String = "none"
  private var arcFault: Boolean = false

  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var pollTimer: ScheduledFuture<*>? = null

  init {
    enterMonitoring()
    println("[ELECTRICAL] Initialized in MONITORING state")
  }

  // Pub/sub emission
  private fun emitArcFaultDetected() {
    daprClient.publishEvent(
      "pubsub", "arcFaultDetected",
      mapOf("arcFaultLocation" to arcFaultLocation)
    ).subscribe()
    println("[ELECTRICAL] EMITTED arcFaultDetected (location=$arcFaultLocation)")
  }

  // Polling
  private fun cancelPoll() {
    pollTimer?.cancel(false)
    pollTimer = null
  }

  private fun schedulePoll() {
    cancelPoll()
    pollTimer = scheduler.schedule({ pollArcFault() }, 30000, TimeUnit.MILLISECONDS)
  }

  private fun pollArcFault() {
    service.checkArcFault { location ->
      onCheckDone(location)
    }
  }

  // State entries
  private fun enterMonitoring() {
    state = State.MONITORING
    pollArcFault()
    schedulePoll()
    println("[ELECTRICAL] -> MONITORING")
  }

  private fun enterArcFault() {
    state = State.ARC_FAULT
    cancelPoll()
    service.tripCircuitBreaker(arcFaultLocation)
    emitArcFaultDetected()
    arcFault = true
    println("[ELECTRICAL] -> ARC_FAULT (location=$arcFaultLocation)")
  }

  private fun enterAcknowledged() {
    state = State.ACKNOWLEDGED
    arcFault = false
    service.acknowledgedElectrical { enterMonitoring() }
    println("[ELECTRICAL] -> ACKNOWLEDGED")
  }

  // Internal
  private fun onCheckDone(location: String) {
    arcFaultLocation = location
    if (state == State.MONITORING && location != "none") {
      enterArcFault()
    } else if (state == State.MONITORING) {
      schedulePoll()
    }
  }

  // External
  override fun onResetElectricalFault() {
    when (state) {
      State.ARC_FAULT -> enterAcknowledged()
      else -> {}
    }
  }
}
