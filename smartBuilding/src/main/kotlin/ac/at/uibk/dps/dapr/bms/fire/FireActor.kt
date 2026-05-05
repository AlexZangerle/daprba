package ac.at.uibk.dps.dapr.bms.fire

import io.dapr.actors.ActorType

/** Fire detection actor interface. */
@ActorType(name = "FireActor")
interface FireActor {

  // Sensor input
  fun onSensorFireDataReceived(imageData: Map<String, Any>)

  // Manual disarm
  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onDisarmSmokeAlert(data: Map<String, Any>)
}
