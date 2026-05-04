package ac.at.uibk.dps.dapr.bms.roomOccupancy

import io.dapr.actors.ActorType

@ActorType(name = "RoomOccupancyActor")
interface RoomOccupancyActor {
  fun onSensorOccupancyReceived(imageData: String)

  fun onActivateEnergySaving()

  fun onMaintenanceDone()

  fun onServiceTimeoutTimer()
}
