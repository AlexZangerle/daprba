package ac.at.uibk.dps.dapr.bms.roomOccupancy

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class RoomOccupancyActorImpl(
  runtimeContext: ActorRuntimeContext<RoomOccupancyActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), RoomOccupancyActor {

  enum class OccupancyState {
    VACANT,
    OCCUPIED,
    TRANSIENT,
  }

  enum class MonitorState {
    IDLE,
    MONITORING,
    MAINTENANCE,
  }

  private var occupancyState: OccupancyState = OccupancyState.VACANT
  private var monitorState: MonitorState = MonitorState.IDLE
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"
  private var occupancyDetected: Boolean = false
  private var energySaving: Boolean = false
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

  private fun cancelTimer() {
    super.unregisterTimer("serviceTimeoutTimer").subscribe()
  }

  override fun onServiceTimeoutTimer() {
    if (monitorState == MonitorState.MONITORING) {
      monitorState = MonitorState.MAINTENANCE
      service.maintenance(roomId)
    }
  }

  private fun onDetectionDone(detected: Boolean) {
    occupancyDetected = detected
    if (monitorState == MonitorState.MONITORING) {
      monitorState = MonitorState.IDLE
      cancelTimer()
    }
    when (occupancyState) {
      OccupancyState.VACANT ->
        if (occupancyDetected) {
          occupancyState = OccupancyState.OCCUPIED
          val now = Clock.System.now()
          val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
          daprClient
            .publishEvent(
              "pubsub",
              "occupancyDetectedEvent",
              mapOf("roomId" to roomId, "time" to epochNanos),
            )
            .subscribe()
        }
      OccupancyState.OCCUPIED ->
        if (occupancyDetected) {
          val now = Clock.System.now()
          val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
          daprClient
            .publishEvent(
              "pubsub",
              "occupancyDetectedEvent",
              mapOf("roomId" to roomId, "time" to epochNanos),
            )
            .subscribe()
        } else {
          occupancyState = OccupancyState.TRANSIENT
          val now = Clock.System.now()
          val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
          daprClient
            .publishEvent(
              "pubsub",
              "occupancyTransient",
              mapOf("roomId" to roomId, "time" to epochNanos),
            )
            .subscribe()
        }
      OccupancyState.TRANSIENT ->
        if (occupancyDetected) {
          occupancyState = OccupancyState.OCCUPIED
          val now = Clock.System.now()
          val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
          daprClient
            .publishEvent(
              "pubsub",
              "occupancyDetectedEvent",
              mapOf("roomId" to roomId, "time" to epochNanos),
            )
            .subscribe()
        } else {
          occupancyState = OccupancyState.VACANT
          val now = Clock.System.now()
          val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
          daprClient
            .publishEvent(
              "pubsub",
              "eOccupancyVacant",
              mapOf("roomId" to roomId, "time" to epochNanos),
            )
            .subscribe()
        }
    }
  }

  override fun onSensorOccupancyReceived(data: Map<String, Any>) {
    measureEventTime(data)
    val imageData = data["imageData"] as? String ?: ""
    if (monitorState == MonitorState.IDLE) {
      monitorState = MonitorState.MONITORING
      cancelTimer()
      super.registerActorTimer(
          "serviceTimeoutTimer",
          "onServiceTimeoutTimer",
          ByteArray(0),
          Duration.ofMillis(5000),
          Duration.ofMillis(-1),
        )
        .subscribe()
    }
    service.detectOccupancy(imageData) { detected -> onDetectionDone(detected) }
  }

  override fun onActivateEnergySaving(data: Map<String, Any>) {
    measureEventTime(data)
    energySaving = true
    if (occupancyState == OccupancyState.OCCUPIED) {
      occupancyState = OccupancyState.TRANSIENT
      val now = Clock.System.now()
      val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
      daprClient
        .publishEvent(
          "pubsub",
          "occupancyTransient",
          mapOf("roomId" to roomId, "time" to epochNanos),
        )
        .subscribe()
    }
  }

  override fun onMaintenanceDone(data: Map<String, Any>) {
    measureEventTime(data)
    if (monitorState == MonitorState.MAINTENANCE) {
      monitorState = MonitorState.IDLE
      cancelTimer()
    }
  }
}
