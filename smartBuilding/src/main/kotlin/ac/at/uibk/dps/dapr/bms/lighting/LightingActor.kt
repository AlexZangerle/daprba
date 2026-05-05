package ac.at.uibk.dps.dapr.bms.lighting

import io.dapr.actors.ActorType

@ActorType(name = "LightingActor")
interface LightingActor {
  fun initialize()

  fun onOccupancyDetected(data: Map<String, Any>)

  fun onOccupancyTransient(data: Map<String, Any>)

  fun onOccupancyVacant(data: Map<String, Any>)

  fun onActivateLightUserLevel(data: Map<String, Any>)

  fun onDeactivateLightUserLevel(data: Map<String, Any>)

  fun onActivateEnergySaving(data: Map<String, Any>)

  fun onDeactivateEnergySaving(data: Map<String, Any>)

  fun onFireAlarm(data: Map<String, Any>)

  fun onGasLeakDetected(data: Map<String, Any>)

  fun onArcFaultDetected(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onGasPurged(data: Map<String, Any>)

  fun onResetElectricalFault(data: Map<String, Any>)

  fun onFlashAllLights(data: Map<String, Any>)

  fun onClearSecurityAlert(data: Map<String, Any>)

  fun onFlashTimer()
}
