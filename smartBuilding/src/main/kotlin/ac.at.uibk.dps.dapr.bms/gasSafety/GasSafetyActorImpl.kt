package ac.at.uibk.dps.dapr.bms.gasSafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class GasSafetyActorImpl(
  runtimeContext: ActorRuntimeContext<GasSafetyActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), GasSafetyActor {

  enum class State {
    MONITORING,
    GAS_LEAK,
    ACKNOWLEDGED,
  }

  private var state: State = State.MONITORING
  private var gasLeakLocation: String = "none"
  private var isGasActive: Boolean = false
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var pollTimer: ScheduledFuture<*>? = null

  override fun initialize() {
    schedulePoll()
  }

  private fun schedulePoll() {
    pollTimer?.cancel(false)
    pollTimer =
      scheduler.schedule(
        {
          service.checkGasLeak { location ->
            gasLeakLocation = location
            if (state == State.MONITORING && location != "none") enterGasLeak()
            else if (state == State.MONITORING) schedulePoll()
          }
        },
        30000,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun enterGasLeak() {
    state = State.GAS_LEAK
    pollTimer?.cancel(false)
    service.closeGasValve(gasLeakLocation)
    service.cutPower(gasLeakLocation)
    daprClient
      .publishEvent("pubsub", "gasLeakDetected", mapOf("gasLeakLocation" to gasLeakLocation))
      .subscribe()
    isGasActive = true
  }

  override fun onGasPurged() {
    if (state == State.GAS_LEAK) {
      isGasActive = false
      service.gasLeakPurged()
      state = State.MONITORING
      schedulePoll()
    }
  }
}
