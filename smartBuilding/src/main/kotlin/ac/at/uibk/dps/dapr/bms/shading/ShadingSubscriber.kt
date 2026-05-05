package ac.at.uibk.dps.dapr.bms.shading

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

/** Pub/sub subscriber for the shading actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "shading")
class ShadingSubscriber : ApplicationListener<ApplicationReadyEvent> {

  private val actorId = System.getenv("ACTOR_ID") ?: "shading-0"
  private val proxy: ShadingActor =
    ActorProxyBuilder(ShadingActor::class.java, ActorClient()).build(ActorId(actorId))

  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    proxy.initialize()
  }

  @Topic(name = "userLevelBlinds", pubsubName = "pubsub")
  @PostMapping("/userLevelBlinds")
  fun handleUserLevelBlinds(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onActivateBlindsUserLevel(event.data)
  }

  @Topic(name = "temperatureBlinds", pubsubName = "pubsub")
  @PostMapping("/temperatureBlinds")
  fun handleDeactivateBlindsUserLevel(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDeactivateBlindsUserLevel(event.data)
  }

  @Topic(name = "lockBlinds", pubsubName = "pubsub")
  @PostMapping("/lockBlinds")
  fun handleLockBlinds(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onLockBlinds(event.data)
  }

  @Topic(name = "unlockBlinds", pubsubName = "pubsub")
  @PostMapping("/unlockBlinds")
  fun handleUnlockBlinds(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onUnlockBlinds(event.data)
  }

  @Topic(name = "zoneActive", pubsubName = "pubsub")
  @PostMapping("/zoneActive")
  fun handleZoneActive(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onZoneActive(event.data)
  }

  @Topic(name = "zoneInactive", pubsubName = "pubsub")
  @PostMapping("/zoneInactive")
  fun handleZoneInactive(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onZoneInactive(event.data)
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

  @Topic(name = "smokeAlert", pubsubName = "pubsub")
  @PostMapping("/smokeAlert")
  fun handleSmokeAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onSmokeAlert(event.data)
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onGasLeakDetected(event.data)
  }

  @Topic(name = "arcFaultDetected", pubsubName = "pubsub")
  @PostMapping("/arcFaultDetected")
  fun handleArcFaultDetected(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onArcFaultDetected(event.data)
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

  @Topic(name = "disarmSmokeAlert", pubsubName = "pubsub")
  @PostMapping("/disarmSmokeAlert")
  fun handleDisarmSmokeAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDisarmSmokeAlert(event.data)
  }

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onResetElectricalFault(event.data)
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onClearSecurityAlert(event.data)
  }
}
