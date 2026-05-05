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
  fun handleForcedEntry(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onForcedEntry(event.data)
  }

  @Topic(name = "tamperDetected", pubsubName = "pubsub")
  @PostMapping("/tamperDetected")
  fun handleTamperDetected(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onTamperDetected(event.data)
  }

  @Topic(name = "manualSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/manualSecurityAlert")
  fun handleManualSecurityAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onManualSecurityAlert(event.data)
  }

  @Topic(name = "accessDenied", pubsubName = "pubsub")
  @PostMapping("/accessDenied")
  fun handleAccessDenied(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onAccessDenied(event.data)
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onClearSecurityAlert(event.data)
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
}
