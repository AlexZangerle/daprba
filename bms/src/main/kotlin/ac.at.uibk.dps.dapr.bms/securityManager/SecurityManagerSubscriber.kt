package ac.at.uibk.dps.dapr.bms.securityManager

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "securityManager")
class SecurityManagerSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "securityManager-0"
  private val actorClient = ActorClient()
  private val proxy: SecurityManagerActor =
    ActorProxyBuilder(SecurityManagerActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "forcedEntry", pubsubName = "pubsub")
  @PostMapping("/forcedEntry")
  fun handleForcedEntry(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val doorId = data["doorId"] as? String ?: ""
    val zoneId = data["zoneId"] as? String ?: ""
    proxy.onForcedEntry(doorId, zoneId)
  }

  @Topic(name = "tamperDetected", pubsubName = "pubsub")
  @PostMapping("/tamperDetected")
  fun handleTamperDetected(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val deviceId = data["deviceId"] as? String ?: ""
    val location = data["location"] as? String ?: ""
    proxy.onTamperDetected(deviceId, location)
  }

  @Topic(name = "manualSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/manualSecurityAlert")
  fun handleManualSecurityAlert(@RequestBody body: Map<String, Any>) {
    proxy.onManualSecurityAlert()
  }

  @Topic(name = "accessDenied", pubsubName = "pubsub")
  @PostMapping("/accessDenied")
  fun handleAccessDenied(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val doorId = data["doorId"] as? String ?: ""
    val user = data["user"] as? String ?: ""
    proxy.onAccessDenied(doorId, user)
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody body: Map<String, Any>) {
    proxy.onClearSecurityAlert()
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
}
