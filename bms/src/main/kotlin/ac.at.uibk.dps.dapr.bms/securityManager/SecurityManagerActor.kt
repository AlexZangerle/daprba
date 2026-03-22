package ac.at.uibk.dps.dapr.bms.securityManager

import io.dapr.actors.ActorType

/**
 * Security manager actor interface.
 *
 */
@ActorType(name = "SecurityManagerActor")
interface SecurityManagerActor {
  fun onForcedEntry(data: Map<String, String>)
  fun onTamperDetected(data: Map<String, String>)
  fun onManualSecurityAlert()
  fun onAccessDenied(data:Map<String, String>)
  fun onClearSecurityAlert()
  fun onFireAlarm()
  fun onGasLeakDetected()
  fun onDisarmFireAlarm()
  fun onGasPurged()
}
