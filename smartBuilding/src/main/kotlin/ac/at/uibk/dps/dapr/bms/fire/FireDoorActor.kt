package ac.at.uibk.dps.dapr.bms.fire

import io.dapr.actors.ActorType

/** Fire door actor interface. */
@ActorType(name = "FireDoorActor")
interface FireDoorActor {
  fun initialize()

  fun onFireAlarm(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)
}
