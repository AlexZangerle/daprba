package ac.at.uibk.dps.dapr.bms.security

import io.dapr.actors.ActorType

@ActorType(name = "SecurityManagerActor")
interface SecurityManagerActor {
  fun onForcedEntry(data: Map<String, Any>)

  fun onTamperDetected(data: Map<String, Any>)

  fun onManualSecurityAlert(data: Map<String, Any>)

  fun onAccessDenied(data: Map<String, Any>)

  fun onClearSecurityAlert(data: Map<String, Any>)

  fun onFireAlarm(data: Map<String, Any>)

  fun onGasLeakDetected(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onGasPurged(data: Map<String, Any>)

  fun onDecayTimer()
}
