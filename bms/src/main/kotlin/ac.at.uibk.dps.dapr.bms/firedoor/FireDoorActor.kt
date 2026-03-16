package ac.at.uibk.dps.dapr.bms.firedoor

import io.dapr.actors.ActorType

/**
 * Fire door actor interface.
 *
 */
@ActorType(name = "FireDoorActor")
interface FireDoorActor {
  fun onFireAlarm()
  fun onDisarmFireAlarm()
}
