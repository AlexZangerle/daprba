package ac.at.uibk.dps.dapr.bms.lighting

import io.dapr.actors.ActorType

/** Lighting actor interface. */
@ActorType(name = "LightingActor")
interface LightingActor {

  // Occupancy events
  fun onOccupancyDetected()

  fun onOccupancyTransient()

  fun onOccupancyVacant()

  // User control events
  fun onActivateLightUserLevel(lightLevel: Int)

  fun onDeactivateLightUserLevel()

  // Energy events
  fun onActivateEnergySaving()

  fun onDeactivateEnergySaving()

  // Safety events
  fun onFireAlarm(emergencyInRoom: String)

  fun onGasLeakDetected()

  fun onArcFaultDetected()

  // Safety recovery events
  fun onDisarmFireAlarm()

  fun onGasPurged()

  fun onResetElectricalFault()

  // Security events
  fun onFlashAllLights()

  fun onClearSecurityAlert()
}
