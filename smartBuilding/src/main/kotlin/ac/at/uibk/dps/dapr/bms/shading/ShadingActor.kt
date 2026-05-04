package ac.at.uibk.dps.dapr.bms.shading

import io.dapr.actors.ActorType

@ActorType(name = "ShadingActor")
interface ShadingActor {
  fun initialize()

  fun onActivateBlindsUserLevel(blindLevel: Int)

  fun onDeactivateBlindsUserLevel()

  fun onLockBlinds()

  fun onUnlockBlinds()

  fun onZoneActive()

  fun onZoneInactive()

  fun onLockdownZone()

  fun onFireAlarm()

  fun onSmokeAlert()

  fun onGasLeakDetected()

  fun onArcFaultDetected()

  fun onDisarmFireAlarm()

  fun onGasPurged()

  fun onDisarmSmokeAlert()

  fun onResetElectricalFault()

  fun onClearSecurityAlert()

  fun onTempPollTimer()
}
