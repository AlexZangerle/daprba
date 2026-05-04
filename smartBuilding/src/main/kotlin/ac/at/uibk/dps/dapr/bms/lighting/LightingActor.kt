package ac.at.uibk.dps.dapr.bms.lighting

import io.dapr.actors.ActorType

@ActorType(name = "LightingActor")
interface LightingActor {
  fun initialize()

  fun onOccupancyDetected()

  fun onOccupancyTransient()

  fun onOccupancyVacant()

  fun onActivateLightUserLevel(lightLevel: Int)

  fun onDeactivateLightUserLevel()

  fun onActivateEnergySaving()

  fun onDeactivateEnergySaving()

  fun onFireAlarm(emergencyInRoom: String)

  fun onGasLeakDetected()

  fun onArcFaultDetected()

  fun onDisarmFireAlarm()

  fun onGasPurged()

  fun onResetElectricalFault()

  fun onFlashAllLights()

  fun onClearSecurityAlert()

  fun onFlashTimer()
}
