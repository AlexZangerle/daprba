package ac.at.uibk.dps.dapr.bms.roomOccupancy

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration

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

  private fun cancelTimer() {
    super.unregisterActorTimer("serviceTimeoutTimer").block()
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
          daprClient
            .publishEvent("pubsub", "occupancyDetectedEvent", mapOf("roomId" to roomId))
            .subscribe()
        }
      OccupancyState.OCCUPIED ->
        if (occupancyDetected) {
          daprClient
            .publishEvent("pubsub", "occupancyDetectedEvent", mapOf("roomId" to roomId))
            .subscribe()
        } else {
          occupancyState = OccupancyState.TRANSIENT
          daprClient
            .publishEvent("pubsub", "occupancyTransient", mapOf("roomId" to roomId))
            .subscribe()
        }
      OccupancyState.TRANSIENT ->
        if (occupancyDetected) {
          occupancyState = OccupancyState.OCCUPIED
          daprClient
            .publishEvent("pubsub", "occupancyDetectedEvent", mapOf("roomId" to roomId))
            .subscribe()
        } else {
          occupancyState = OccupancyState.VACANT
          daprClient
            .publishEvent("pubsub", "eOccupancyVacant", mapOf("roomId" to roomId))
            .subscribe()
        }
    }
  }

  override fun onSensorOccupancyReceived(imageData: String) {
    if (monitorState == MonitorState.IDLE) {
      monitorState = MonitorState.MONITORING
      cancelTimer()
      super.registerActorTimer(
          "serviceTimeoutTimer",
          "onServiceTimeoutTimer",
          Duration.ofMillis(5000),
          Duration.ofMillis(-1),
        )
        .block()
    }
    service.detectOccupancy(imageData) { detected -> onDetectionDone(detected) }
  }

  override fun onActivateEnergySaving() {
    energySaving = true
    if (occupancyState == OccupancyState.OCCUPIED) {
      occupancyState = OccupancyState.TRANSIENT
      daprClient.publishEvent("pubsub", "occupancyTransient", mapOf("roomId" to roomId)).subscribe()
    }
  }

  override fun onMaintenanceDone() {
    if (monitorState == MonitorState.MAINTENANCE) {
      monitorState = MonitorState.IDLE
      cancelTimer()
    }
  }
}
