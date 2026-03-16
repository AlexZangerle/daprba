package ac.at.uibk.dps.dapr.bms.roomoccupancy

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Room occupancy actor implementation.
 *
 * Main SM: VACANT -> OCCUPIED -> TRANSIENT
 * Monitor SM: IDLE -> MONITORING -> MAINTENANCE
 */
class RoomOccupancyActorImpl(
  runtimeContext: ActorRuntimeContext<RoomOccupancyActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), RoomOccupancyActor {

  // Main SM states
  enum class OccupancyState { VACANT, OCCUPIED, TRANSIENT }

  // Monitor SM states
  enum class MonitorState { IDLE, MONITORING, MAINTENANCE }

  private var occupancyState: OccupancyState = OccupancyState.VACANT
  private var monitorState: MonitorState = MonitorState.IDLE
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"

  private var occupancyDetected: Boolean = false
  private var energySaving: Boolean = false
  private val daprClient = DaprClientBuilder().build()

  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var serviceTimeoutTimer: ScheduledFuture<*>? = null

  init {
    // CSML: vacant entry sets currentOccupancy = 'vacant'
    println("[OCCUPANCY] Initialized VACANT, monitor IDLE, roomId=$roomId")
  }

  // Pub/sub emission
  private fun emitOccupancyDetected() {
    daprClient.publishEvent("pubsub", "occupancyDetectedEvent", mapOf("roomId" to roomId))
      .subscribe()
    println("[OCCUPANCY] EMITTED occupancyDetectedEvent")
  }

  private fun emitOccupancyTransient() {
    daprClient.publishEvent("pubsub", "occupancyTransient", mapOf("roomId" to roomId))
      .subscribe()
    println("[OCCUPANCY] EMITTED occupancyTransient")
  }

  private fun emitOccupancyVacant() {
    daprClient.publishEvent("pubsub", "eOccupancyVacant", mapOf("roomId" to roomId))
      .subscribe()
    println("[OCCUPANCY] EMITTED eOccupancyVacant")
  }

  // Monitor SM
  private fun monitorStartMonitoring() {
    monitorState = MonitorState.MONITORING
    serviceTimeoutTimer?.cancel(false)
    serviceTimeoutTimer = scheduler.schedule({
      onMonitorTimeout()
    }, 5000, TimeUnit.MILLISECONDS)
  }

  private fun monitorReturnToIdle() {
    monitorState = MonitorState.IDLE
    serviceTimeoutTimer?.cancel(false)
    serviceTimeoutTimer = null
  }

  private fun onMonitorTimeout() {
    if (monitorState == MonitorState.MONITORING) {
      monitorState = MonitorState.MAINTENANCE
      service.maintenance(roomId)
      println("[OCCUPANCY] Monitor -> MAINTENANCE (service timeout)")
    }
  }

  // Internal
  private fun onDetectionDone(detected: Boolean) {
    occupancyDetected = detected

    if (monitorState == MonitorState.MONITORING) {
      monitorReturnToIdle()
    }

    when (occupancyState) {
      OccupancyState.VACANT -> {
        if (occupancyDetected) {
          occupancyState = OccupancyState.OCCUPIED
          println("[OCCUPANCY] -> OCCUPIED")
          emitOccupancyDetected()
        }
      }
      OccupancyState.OCCUPIED -> {
        if (occupancyDetected) {
          emitOccupancyDetected()
        } else {
          occupancyState = OccupancyState.TRANSIENT
          println("[OCCUPANCY] -> TRANSIENT")
          emitOccupancyTransient()
        }
      }
      OccupancyState.TRANSIENT -> {
        if (occupancyDetected) {
          occupancyState = OccupancyState.OCCUPIED
          println("[OCCUPANCY] -> OCCUPIED")
          emitOccupancyDetected()
        } else {
          occupancyState = OccupancyState.VACANT
          println("[OCCUPANCY] -> VACANT")
          emitOccupancyVacant()
        }
      }
    }
  }

  // External event handlers
  override fun onSensorOccupancyReceived(imageData: String) {
    if (monitorState == MonitorState.IDLE) {
      monitorStartMonitoring()
    }
    service.detectOccupancy(imageData) { detected ->
      onDetectionDone(detected)
    }
  }

  override fun onActivateEnergySaving() {
    energySaving = true
    when (occupancyState) {
      OccupancyState.OCCUPIED -> {
        occupancyState = OccupancyState.TRANSIENT
        println("[OCCUPANCY] -> TRANSIENT (energy saving)")
        emitOccupancyTransient()
      }
      else -> {}
    }
  }

  override fun onMaintenanceDone() {
    if (monitorState == MonitorState.MAINTENANCE) {
      monitorReturnToIdle()
      println("[OCCUPANCY] Monitor -> IDLE (maintenance done)")
    }
  }
}
