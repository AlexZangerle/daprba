package ac.at.uibk.dps.dapr.bms.hvac

import io.dapr.actors.ActorType

/**
 * HVAC actor interface.
 *
 */
@ActorType(name = "HvacActor")
interface HvacActor {

  // Occupancy events
  fun onOccupancyDetected()
  fun onOccupancyVacant()

  // Zone events
  fun onZoneActive()
  fun onZoneInactive()

  // Schedule events
  fun onEnterBusinessHours()
  fun onEnterAfterHours()
  fun onEnterWeekend()

  // Energy events
  fun onActivateEnergySaving()
  fun onDrasticEnergySaving()
  fun onDeactivateEnergySaving()
  fun onEnergyShutdown()

  // Safety events
  fun onFireAlarm()
  fun onGasLeakDetected()
  fun onSmokeAlert()
  fun onArcFaultDetected()

  // Safety recovery events
  fun onDisarmFireAlarm()
  fun onGasPurged()
  fun onDisarmSmokeAlert()
  fun onResetElectricalFault()
}
