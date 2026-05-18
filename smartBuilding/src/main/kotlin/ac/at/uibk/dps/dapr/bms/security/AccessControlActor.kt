package ac.at.uibk.dps.dapr.bms.security

import io.dapr.actors.ActorType

@ActorType(name = "AccessControlActor")
interface AccessControlActor {
  fun initialize()

  fun onAuthenticationRequest(data: Map<String, Any>)

  fun onForceUnlockRequest(data: Map<String, Any>)

  fun onPhysicalTamper(data: Map<String, Any>)

  fun onDoorForcedOpen(data: Map<String, Any>)

  fun onLockdownZone(data: Map<String, Any>)

  fun onFireAlarm(data: Map<String, Any>)

  fun onGasLeakDetected(data: Map<String, Any>)

  fun onunlockEvacuationRoutes(data: Map<String, Any>)

  fun onClearSecurityAlert(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onGasPurged(data: Map<String, Any>)

  fun onEnterBusinessHours(data: Map<String, Any>)

  fun onEnterAfterHours(data: Map<String, Any>)

  fun onEnterWeekend(data: Map<String, Any>)

  fun onAccessTimer()
}
