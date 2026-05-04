package ac.at.uibk.dps.dapr.bms.electricalsafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration

class ElectricalSafetyActorImpl(
  runtimeContext: ActorRuntimeContext<ElectricalSafetyActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), ElectricalSafetyActor {

  enum class State {
    MONITORING,
    ARC_FAULT,
    ACKNOWLEDGED,
  }

  private var state: State = State.MONITORING
  private var arcFaultLocation: String = "none"
  private var arcFault: Boolean = false
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  override fun initialize() {
    schedulePoll()
  }

  private fun schedulePoll() {
    super.registerActorTimer(
        "pollTimer",
        "onPollTimer",
        Duration.ofMillis(1000),
        Duration.ofMillis(-1),
      )
      .block()
  }

  private fun cancelPoll() {
    super.unregisterActorTimer("pollTimer").block()
  }

  override fun onPollTimer() {
    if (state != State.MONITORING) return
    service.checkArcFault { location ->
      arcFaultLocation = location
      if (state == State.MONITORING && location != "none") enterArcFault()
      else if (state == State.MONITORING) schedulePoll()
    }
  }

  private fun enterArcFault() {
    state = State.ARC_FAULT
    cancelPoll()
    service.tripCircuitBreaker(arcFaultLocation)
    daprClient
      .publishEvent("pubsub", "arcFaultDetected", mapOf("arcFaultLocation" to arcFaultLocation))
      .subscribe()
    arcFault = true
  }

  private fun enterAcknowledged() {
    state = State.ACKNOWLEDGED
    arcFault = false
    service.acknowledgedElectrical { enterMonitoring() }
  }

  private fun enterMonitoring() {
    state = State.MONITORING
    schedulePoll()
  }

  override fun onResetElectricalFault() {
    if (state == State.ARC_FAULT) enterAcknowledged()
  }
}
