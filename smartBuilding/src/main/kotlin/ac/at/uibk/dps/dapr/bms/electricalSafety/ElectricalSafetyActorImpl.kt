package ac.at.uibk.dps.dapr.bms.electricalSafety

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import ac.at.uibk.dps.dapr.bms.electricalsafety.ElectricalSafetyActor
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

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

  private val metricsRegistry = BMS.metricsRegistry

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
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient
      .publishEvent(
        "pubsub",
        "arcFaultDetected",
        mapOf("arcFaultLocation" to arcFaultLocation, "time" to epochNanos),
      )
      .subscribe()
    arcFault = true
  }

  private fun enterAcknowledged() {
    state = State.ACKNOWLEDGED
    arcFault = false
    service.acknowledgedElectrical()
    enterMonitoring()
  }

  private fun enterMonitoring() {
    state = State.MONITORING
    schedulePoll()
  }

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun onResetElectricalFault(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.ARC_FAULT) enterAcknowledged()
  }
}
