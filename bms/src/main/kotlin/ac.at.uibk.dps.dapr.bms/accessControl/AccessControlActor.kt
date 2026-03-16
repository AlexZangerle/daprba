package ac.at.uibk.dps.dapr.bms.accessControl

import io.dapr.actors.ActorType

/**
 * Access control actor interface.
 *
 */
@ActorType(name = "AccessControlActor")
interface AccessControlActor {
  fun onAuthenticationRequest(cardId: String)
  fun onForceUnlockRequest()
  fun onPhysicalTamper()
  fun onDoorForcedOpen()
  fun onLockdownZone()
  fun onFireAlarm()
  fun onGasLeakDetected()
  fun onUnlockAllEvacuationRoutes()
  fun onClearSecurityAlert()
  fun onDisarmFireAlarm()
  fun onGasPurged()
  fun onEnterBusinessHours()
  fun onEnterAfterHours()
  fun onEnterWeekend()
}
