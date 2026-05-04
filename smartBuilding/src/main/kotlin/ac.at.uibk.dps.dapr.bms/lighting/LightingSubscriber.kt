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

  // Occupancy events
  @Topic(name = "occupancyDetectedEvent", pubsubName = "pubsub")
  @PostMapping("/occupancyDetectedEvent")
  fun handleOccupancyDetected() {
    proxy.onOccupancyDetected()
  }

  @Topic(name = "occupancyTransient", pubsubName = "pubsub")
  @PostMapping("/occupancyTransient")
  fun handleOccupancyTransient() {
    proxy.onOccupancyTransient()
  }

  @Topic(name = "eOccupancyVacant", pubsubName = "pubsub")
  @PostMapping("/eOccupancyVacant")
  fun handleOccupancyVacant() {
    proxy.onOccupancyVacant()
  }

  // User control events
  @Topic(name = "userLevelLight", pubsubName = "pubsub")
  @PostMapping("/userLevelLight")
  fun handleUserLevelLight(@RequestBody event: CloudEvent<Map<String, Any>>) {
    val lightLevel = (event.data["lightLevel"] as? Number)?.toInt() ?: 0
    proxy.onActivateLightUserLevel(lightLevel)
  }

  @Topic(name = "occupancyLight", pubsubName = "pubsub")
  @PostMapping("/occupancyLight")
  fun handleDeactivateLightUserLevel() {
    proxy.onDeactivateLightUserLevel()
  }

  // Energy events
  @Topic(name = "energySavingMode", pubsubName = "pubsub")
  @PostMapping("/energySavingMode")
  fun handleActivateEnergySaving() {
    proxy.onActivateEnergySaving()
  }

  @Topic(name = "energyNormalMode", pubsubName = "pubsub")
  @PostMapping("/energyNormalMode")
  fun handleDeactivateEnergySaving() {
    proxy.onDeactivateEnergySaving()
  }

  // Safety events
  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody event: CloudEvent<Map<String, Any>>) {
    val emergencyInRoom = event.data["emergencyInRoom"] as? String ?: "unknown"
    proxy.onFireAlarm(emergencyInRoom)
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected() {
    proxy.onGasLeakDetected()
  }

  @Topic(name = "arcFaultDetected", pubsubName = "pubsub")
  @PostMapping("/arcFaultDetected")
  fun handleArcFaultDetected() {
    proxy.onArcFaultDetected()
  }

  // Safety recovery events
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

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault() {
    proxy.onResetElectricalFault()
  }

  // Security events
  @Topic(name = "flashAllLights", pubsubName = "pubsub")
  @PostMapping("/flashAllLights")
  fun handleFlashAllLights() {
    proxy.onFlashAllLights()
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert() {
    proxy.onClearSecurityAlert()
  }
}
