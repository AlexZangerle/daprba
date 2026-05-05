package ac.at.uibk.dps.dapr.bms.hvac

import io.dapr.actors.ActorType

@ActorType(name = "HvacActor")
interface HvacActor {
  fun initialize()

  fun onOccupancyDetected(data: Map<String, Any>)

  fun onOccupancyVacant(data: Map<String, Any>)

  fun onZoneActive(data: Map<String, Any>)

  fun onZoneInactive(data: Map<String, Any>)

  fun onEnterBusinessHours(data: Map<String, Any>)

  fun onEnterAfterHours(data: Map<String, Any>)

  fun onEnterWeekend(data: Map<String, Any>)

  fun onActivateEnergySaving(data: Map<String, Any>)

  fun onDrasticEnergySaving(data: Map<String, Any>)

  fun onDeactivateEnergySaving(data: Map<String, Any>)

  fun onEnergyShutdown(data: Map<String, Any>)

  fun onFireAlarm(data: Map<String, Any>)

  fun onGasLeakDetected(data: Map<String, Any>)

  fun onSmokeAlert(data: Map<String, Any>)

  fun onArcFaultDetected(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onGasPurged(data: Map<String, Any>)

  fun onDisarmSmokeAlert(data: Map<String, Any>)

  fun onResetElectricalFault(data: Map<String, Any>)

  fun onTempPollTimer()
}
