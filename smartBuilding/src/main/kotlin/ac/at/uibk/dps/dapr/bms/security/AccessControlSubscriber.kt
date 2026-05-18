package ac.at.uibk.dps.dapr.bms.security

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import io.dapr.client.domain.CloudEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "accessControl")
class AccessControlSubscriber : ApplicationListener<ApplicationReadyEvent> {

  private val actorId = System.getenv("ACTOR_ID") ?: "accessControl-0"
  private val proxy: AccessControlActor =
    ActorProxyBuilder(AccessControlActor::class.java, ActorClient()).build(ActorId(actorId))

  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    proxy.initialize()
  }

  @Topic(name = "authenticationRequest", pubsubName = "pubsub")
  @PostMapping("/authenticationRequest")
  fun handleAuthRequest(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onAuthenticationRequest(event.data)
  }

  @Topic(name = "forceUnlockRequest", pubsubName = "pubsub")
  @PostMapping("/forceUnlockRequest")
  fun handleForceUnlock(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onForceUnlockRequest(event.data)
  }

  @Topic(name = "physicalTamper", pubsubName = "pubsub")
  @PostMapping("/physicalTamper")
  fun handlePhysicalTamper(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onPhysicalTamper(event.data)
  }

  @Topic(name = "doorForcedOpen", pubsubName = "pubsub")
  @PostMapping("/doorForcedOpen")
  fun handleDoorForcedOpen(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDoorForcedOpen(event.data)
  }

  @Topic(name = "lockdownZone", pubsubName = "pubsub")
  @PostMapping("/lockdownZone")
  fun handleLockdownZone(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onLockdownZone(event.data)
  }

  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onFireAlarm(event.data)
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onGasLeakDetected(event.data)
  }

  @Topic(name = "unlockEvacuationRoutes", pubsubName = "pubsub")
  @PostMapping("/unlockEvacuationRoutes")
  fun handleUnlockEvacRoutes(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onunlockEvacuationRoutes(event.data)
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onClearSecurityAlert(event.data)
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDisarmFireAlarm(event.data)
  }

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onGasPurged(event.data)
  }

  @Topic(name = "enterBusinessHours", pubsubName = "pubsub")
  @PostMapping("/enterBusinessHours")
  fun handleEnterBusinessHours(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onEnterBusinessHours(event.data)
  }

  @Topic(name = "enterAfterHours", pubsubName = "pubsub")
  @PostMapping("/enterAfterHours")
  fun handleEnterAfterHours(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onEnterAfterHours(event.data)
  }

  @Topic(name = "enterWeekend", pubsubName = "pubsub")
  @PostMapping("/enterWeekend")
  fun handleEnterWeekend(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onEnterWeekend(event.data)
  }
}
