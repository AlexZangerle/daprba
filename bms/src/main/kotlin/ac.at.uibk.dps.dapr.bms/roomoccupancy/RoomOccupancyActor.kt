package ac.at.uibk.dps.dapr.bms.roomoccupancy

import io.dapr.actors.ActorType

/**
 * Room occupancy actor interface.
 *
 */
@ActorType(name = "RoomOccupancyActor")
interface RoomOccupancyActor {

  // Sensor input
  fun onSensorOccupancyReceived(imageData: String)

  // Energy events
  fun onActivateEnergySaving()

  // Maintenance
  fun onMaintenanceDone()
}
