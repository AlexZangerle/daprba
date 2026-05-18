package ac.at.uibk.dps.dapr.bms.gasSafety

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

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

  private val metricsRegistry = BMS.metricsRegistry

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun initialize() {
    schedulePoll()
  }

  private fun schedulePoll() {
    super.registerActorTimer(
        "pollTimer",
        "onPollTimer",
        ByteArray(0),
        Duration.ofMillis(30000),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  private fun cancelPoll() {
    super.unregisterTimer("pollTimer").subscribe()
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
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent(
        "pubsub",
        "gasLeakDetected",
        mapOf("gasLeakLocation" to gasLeakLocation, "time" to epochNanos),
      )
      .subscribe()
    isGasActive = true
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.GAS_LEAK) {
      isGasActive = false
      service.gasLeakPurged()
      state = State.MONITORING
      schedulePoll()
    }
  }
}
