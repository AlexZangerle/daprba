package ac.at.uibk.dps.dapr.bms.fire

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class FireDoorActorImpl(runtimeContext: ActorRuntimeContext<FireDoorActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), FireDoorActor {

  enum class State {
    OPEN,
    CLOSED,
  }

  private var state: State = State.OPEN
  private val doorId: String = System.getenv("DOOR_ID") ?: "Door 0"
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
    service.openFireDoor(doorId)
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.OPEN) {
      state = State.CLOSED
      service.closeFireDoor(doorId)
    }
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.CLOSED) {
      state = State.OPEN
      service.openFireDoor(doorId)
    }
  }
}
