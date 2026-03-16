package ac.at.uibk.dps.dapr.bms.energymanagement

import io.dapr.actors.ActorType

/**
 * Energy management actor interface.
 *
 */
@ActorType(name = "EnergyManagementActor")
interface EnergyManagementActor {
  fun onEnterBusinessHours()
  fun onEnterAfterHours()
  fun onEnterWeekend()
  fun onFireAlarm()
  fun onGasLeakDetected()
  fun onDisarmFireAlarm()
  fun onGasPurged()
}
