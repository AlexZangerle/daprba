package ac.at.uibk.dps.dapr.bms.securityManager

import io.dapr.actors.ActorType

/**
 * Security manager actor interface.
 *
 */
@ActorType(name = "SecurityManagerActor")
interface SecurityManagerActor {
  fun onForcedEntry(doorId: String, zoneId: String)
  fun onTamperDetected(deviceId: String, location: String)
  fun onManualSecurityAlert()
  fun onAccessDenied(doorId: String, user: String)
  fun onClearSecurityAlert()
  fun onFireAlarm()
  fun onGasLeakDetected()
  fun onDisarmFireAlarm()
  fun onGasPurged()
}
