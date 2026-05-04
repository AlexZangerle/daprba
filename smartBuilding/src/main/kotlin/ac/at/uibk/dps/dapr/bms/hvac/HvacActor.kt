package ac.at.uibk.dps.dapr.bms.hvac

import io.dapr.actors.ActorType

@ActorType(name = "HvacActor")
interface HvacActor {
  fun initialize()

  fun onOccupancyDetected()

  fun onOccupancyVacant()

  fun onZoneActive()

  fun onZoneInactive()

  fun onEnterBusinessHours()

  fun onEnterAfterHours()

  fun onEnterWeekend()

  fun onActivateEnergySaving()

  fun onDrasticEnergySaving()

  fun onDeactivateEnergySaving()

  fun onEnergyShutdown()

  fun onFireAlarm()

  fun onGasLeakDetected()

  fun onSmokeAlert()

  fun onArcFaultDetected()

  fun onDisarmFireAlarm()

  fun onGasPurged()

  fun onDisarmSmokeAlert()

  fun onResetElectricalFault()

  fun onTempPollTimer()
}
