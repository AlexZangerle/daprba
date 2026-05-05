package ac.at.uibk.dps.dapr.bms.roomOccupancy

import io.dapr.actors.ActorType

@ActorType(name = "RoomOccupancyActor")
interface RoomOccupancyActor {
  fun onSensorOccupancyReceived(data: Map<String, Any>)

  fun onActivateEnergySaving(data: Map<String, Any>)

  fun onMaintenanceDone(data: Map<String, Any>)

  fun onServiceTimeoutTimer()
}
