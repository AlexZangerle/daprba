package ac.at.uibk.dps.dapr.bms.security

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import io.dapr.client.domain.CloudEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "securityManager")
class SecurityManagerSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "securityManager-0"
  private val proxy: SecurityManagerActor =
    ActorProxyBuilder(SecurityManagerActor::class.java, ActorClient()).build(ActorId(actorId))

  @Topic(name = "forcedEntry", pubsubName = "pubsub")
  @PostMapping("/forcedEntry")
  fun handleForcedEntry(@RequestBody event: CloudEvent<Map<String, String>>) {
    proxy.onForcedEntry(event.data)
  }

  @Topic(name = "tamperDetected", pubsubName = "pubsub")
  @PostMapping("/tamperDetected")
  fun handleTamperDetected(@RequestBody event: CloudEvent<Map<String, String>>) {
    proxy.onTamperDetected(event.data)
  }

  @Topic(name = "manualSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/manualSecurityAlert")
  fun handleManualSecurityAlert() {
    proxy.onManualSecurityAlert()
  }

  @Topic(name = "accessDenied", pubsubName = "pubsub")
  @PostMapping("/accessDenied")
  fun handleAccessDenied(@RequestBody event: CloudEvent<Map<String, String>>) {
    proxy.onAccessDenied(event.data)
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert() {
    proxy.onClearSecurityAlert()
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
}
