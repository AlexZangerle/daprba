package ac.at.uibk.dps.dapr.bms.accessControl

import ac.at.uibk.dps.dapr.bms.accessControl.AccessControlActor
import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "accessControl")
class AccessControlSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "accessControl-0"
  private val actorClient = ActorClient()
  private val proxy: AccessControlActor =
    ActorProxyBuilder(AccessControlActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "authenticationRequest", pubsubName = "pubsub")
  @PostMapping("/authenticationRequest")
  fun handleAuthRequest(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val cardId = data["cardId"] as? String ?: ""
    proxy.onAuthenticationRequest(cardId)
  }

  @Topic(name = "forceUnlockRequest", pubsubName = "pubsub")
  @PostMapping("/forceUnlockRequest")
  fun handleForceUnlock(@RequestBody body: Map<String, Any>) {
    proxy.onForceUnlockRequest()
  }

  @Topic(name = "physicalTamper", pubsubName = "pubsub")
  @PostMapping("/physicalTamper")
  fun handlePhysicalTamper(@RequestBody body: Map<String, Any>) {
    proxy.onPhysicalTamper()
  }

  @Topic(name = "doorForcedOpen", pubsubName = "pubsub")
  @PostMapping("/doorForcedOpen")
  fun handleDoorForcedOpen(@RequestBody body: Map<String, Any>) {
    proxy.onDoorForcedOpen()
  }

  @Topic(name = "lockdownZone", pubsubName = "pubsub")
  @PostMapping("/lockdownZone")
  fun handleLockdownZone(@RequestBody body: Map<String, Any>) {
    proxy.onLockdownZone()
  }

  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody body: Map<String, Any>) {
    proxy.onFireAlarm()
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected(@RequestBody body: Map<String, Any>) {
    proxy.onGasLeakDetected()
  }

  @Topic(name = "unlockAllEvacuationRoutes", pubsubName = "pubsub")
  @PostMapping("/unlockAllEvacuationRoutes")
  fun handleUnlockEvacRoutes(@RequestBody body: Map<String, Any>) {
    proxy.onUnlockAllEvacuationRoutes()
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody body: Map<String, Any>) {
    proxy.onClearSecurityAlert()
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody body: Map<String, Any>) {
    proxy.onDisarmFireAlarm()
  }

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged(@RequestBody body: Map<String, Any>) {
    proxy.onGasPurged()
  }

  @Topic(name = "enterBusinessHours", pubsubName = "pubsub")
  @PostMapping("/enterBusinessHours")
  fun handleEnterBusinessHours(@RequestBody body: Map<String, Any>) {
    proxy.onEnterBusinessHours()
  }

  @Topic(name = "enterAfterHours", pubsubName = "pubsub")
  @PostMapping("/enterAfterHours")
  fun handleEnterAfterHours(@RequestBody body: Map<String, Any>) {
    proxy.onEnterAfterHours()
  }

  @Topic(name = "enterWeekend", pubsubName = "pubsub")
  @PostMapping("/enterWeekend")
  fun handleEnterWeekend(@RequestBody body: Map<String, Any>) {
    proxy.onEnterWeekend()
  }
}
