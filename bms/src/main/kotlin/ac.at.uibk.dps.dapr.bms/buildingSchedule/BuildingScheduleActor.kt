package ac.at.uibk.dps.dapr.bms.buildingschedule

import io.dapr.actors.ActorType

/**
 * Building schedule actor interface.
 *
 */
@ActorType(name = "BuildingScheduleActor")
interface BuildingScheduleActor {
  fun onFireAlarm()
  fun onGasLeakDetected()
  fun onDisarmFireAlarm()
  fun onGasPurged()
}
