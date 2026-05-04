package ac.at.uibk.dps.dapr.bms.electricalsafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var pollTimer: ScheduledFuture<*>? = null

  override fun initialize() {
    enterMonitoring()
  }

  private fun cancelPoll() {
    pollTimer?.cancel(false)
    pollTimer = null
  }

  private fun enterMonitoring() {
    state = State.MONITORING
    service.checkArcFault { location ->
      arcFaultLocation = location
      if (state == State.MONITORING && location != "none") enterArcFault()
      else if (state == State.MONITORING)
        pollTimer = scheduler.schedule({ enterMonitoring() }, 30000, TimeUnit.MILLISECONDS)
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

  override fun onResetElectricalFault() {
    if (state == State.ARC_FAULT) enterAcknowledged()
  }
}
