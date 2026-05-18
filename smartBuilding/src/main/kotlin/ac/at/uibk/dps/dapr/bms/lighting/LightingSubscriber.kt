package ac.at.uibk.dps.dapr.bms.lighting

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

/** Pub/sub subscriber for the lighting actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "lighting")
class LightingSubscriber : ApplicationListener<ApplicationReadyEvent> {

  private val actorId = System.getenv("ACTOR_ID") ?: "lighting-0"
  private val proxy: LightingActor =
    ActorProxyBuilder(LightingActor::class.java, ActorClient()).build(ActorId(actorId))

  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    proxy.initialize()
  }

  @Topic(name = "occupancyDetected", pubsubName = "pubsub")
  @PostMapping("/occupancyDetected")
  fun handleOccupancyDetected(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onOccupancyDetected(event.data)
  }

  @Topic(name = "occupancyTransient", pubsubName = "pubsub")
  @PostMapping("/occupancyTransient")
  fun handleOccupancyTransient(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onOccupancyTransient(event.data)
  }

  @Topic(name = "occupancyVacant", pubsubName = "pubsub")
  @PostMapping("/occupancyVacant")
  fun handloccupancyVacant(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onOccupancyVacant(event.data)
  }

  @Topic(name = "userLightLevel", pubsubName = "pubsub")
  @PostMapping("/userLightLevel")
  fun handleuserLightLevel(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onActivateLightUserLevel(event.data)
  }

  @Topic(name = "deactivateUserLightLevel", pubsubName = "pubsub")
  @PostMapping("/deactivateUserLightLevel")
  fun handleDeactivateLightUserLevel(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDeactivateLightUserLevel(event.data)
  }

  @Topic(name = "energySaving", pubsubName = "pubsub")
  @PostMapping("/energySaving")
  fun handleActivateEnergySaving(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onActivateEnergySaving(event.data)
  }

  @Topic(name = "deactivateEnergySaving", pubsubName = "pubsub")
  @PostMapping("/deactivateEnergySaving")
  fun handleDeactivateEnergySaving(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDeactivateEnergySaving(event.data)
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

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onResetElectricalFault(event.data)
  }

  @Topic(name = "flashAllLights", pubsubName = "pubsub")
  @PostMapping("/flashAllLights")
  fun handleFlashAllLights(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onFlashAllLights(event.data)
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onClearSecurityAlert(event.data)
  }
}
