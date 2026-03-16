package ac.at.uibk.dps.dapr.bms.shading

import io.dapr.actors.ActorType

/**
 * Shading actor interface.
 *
 */
@ActorType(name = "ShadingActor")
interface ShadingActor {

  // User control events
  fun onActivateBlindsUserLevel(blindLevel: Int)
  fun onDeactivateBlindsUserLevel()
  fun onLockBlinds()
  fun onUnlockBlinds()

  // Zone events
  fun onZoneActive()
  fun onZoneInactive()
  fun onLockdownZone()

  // Safety events
  fun onFireAlarm()
  fun onSmokeAlert()
  fun onGasLeakDetected()
  fun onArcFaultDetected()

  // Safety recovery events
  fun onDisarmFireAlarm()
  fun onGasPurged()
  fun onDisarmSmokeAlert()
  fun onResetElectricalFault()

  // Security events
  fun onClearSecurityAlert()
}
S