package ac.at.uibk.dps.dapr.bms.shading

import io.dapr.actors.ActorType

@ActorType(name = "ShadingActor")
interface ShadingActor {
  fun initialize()

  fun onActivateBlindsUserLevel(data: Map<String, Any>)

  fun onDeactivateBlindsUserLevel(data: Map<String, Any>)

  fun onLockBlinds(data: Map<String, Any>)

  fun onUnlockBlinds(data: Map<String, Any>)

  fun onZoneActive(data: Map<String, Any>)

  fun onZoneInactive(data: Map<String, Any>)

  fun onLockdownZone(data: Map<String, Any>)

  fun onFireAlarm(data: Map<String, Any>)

  fun onSmokeAlert(data: Map<String, Any>)

  fun onGasLeakDetected(data: Map<String, Any>)

  fun onArcFaultDetected(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onGasPurged(data: Map<String, Any>)

  fun onDisarmSmokeAlert(data: Map<String, Any>)

  fun onResetElectricalFault(data: Map<String, Any>)

  fun onClearSecurityAlert(data: Map<String, Any>)

  fun onTempPollTimer()
}
