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
    val cardId = event.data["cardId"] as? String ?: ""
    proxy.onAuthenticationRequest(cardId)
  }

  @Topic(name = "forceUnlockRequest", pubsubName = "pubsub")
  @PostMapping("/forceUnlockRequest")
  fun handleForceUnlock() {
    proxy.onForceUnlockRequest()
  }

  @Topic(name = "physicalTamper", pubsubName = "pubsub")
  @PostMapping("/physicalTamper")
  fun handlePhysicalTamper() {
    proxy.onPhysicalTamper()
  }

  @Topic(name = "doorForcedOpen", pubsubName = "pubsub")
  @PostMapping("/doorForcedOpen")
  fun handleDoorForcedOpen() {
    proxy.onDoorForcedOpen()
  }

  @Topic(name = "lockdownZone", pubsubName = "pubsub")
  @PostMapping("/lockdownZone")
  fun handleLockdownZone() {
    proxy.onLockdownZone()
  }

  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm() {
    proxy.onFireAlarm()
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected() {
    proxy.onGasLeakDetected()
  }

  @Topic(name = "unlockAllEvacuationRoutes", pubsubName = "pubsub")
  @PostMapping("/unlockAllEvacuationRoutes")
  fun handleUnlockEvacRoutes() {
    proxy.onUnlockAllEvacuationRoutes()
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert() {
    proxy.onClearSecurityAlert()
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm() {
    proxy.onDisarmFireAlarm()
  }

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged() {
    proxy.onGasPurged()
  }

  @Topic(name = "enterBusinessHours", pubsubName = "pubsub")
  @PostMapping("/enterBusinessHours")
  fun handleEnterBusinessHours() {
    proxy.onEnterBusinessHours()
  }

  @Topic(name = "enterAfterHours", pubsubName = "pubsub")
  @PostMapping("/enterAfterHours")
  fun handleEnterAfterHours() {
    proxy.onEnterAfterHours()
  }

  @Topic(name = "enterWeekend", pubsubName = "pubsub")
  @PostMapping("/enterWeekend")
  fun handleEnterWeekend() {
    proxy.onEnterWeekend()
  }
}
