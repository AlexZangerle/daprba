package ac.at.uibk.dps.dapr.bms.gasSafety

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration

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
    service.checkGasLeak { location ->
      gasLeakLocation = location
      if (state == State.MONITORING && location != "none") enterGasLeak()
      else if (state == State.MONITORING) schedulePoll()
    }
  }

  private fun enterGasLeak() {
    state = State.GAS_LEAK
    cancelPoll()
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
