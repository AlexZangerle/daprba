package ac.at.uibk.dps.dapr.bms.lighting

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the lighting actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "lighting")
class LightingSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "lighting-0"
  private val actorClient = ActorClient()
  private val proxy: LightingActor =
    ActorProxyBuilder(LightingActor::class.java, actorClient).build(ActorId(actorId))

  @PostConstruct
  fun initialize() {
    proxy.initialize()
  }

  // Occupancy events
  @Topic(name = "occupancyDetectedEvent", pubsubName = "pubsub")
  @PostMapping("/occupancyDetectedEvent")
  fun handleOccupancyDetected(@RequestBody body: Map<String, Any>) {
    proxy.onOccupancyDetected()
  }

  @Topic(name = "occupancyTransient", pubsubName = "pubsub")
  @PostMapping("/occupancyTransient")
  fun handleOccupancyTransient(@RequestBody body: Map<String, Any>) {
    proxy.onOccupancyTransient()
  }

  @Topic(name = "eOccupancyVacant", pubsubName = "pubsub")
  @PostMapping("/eOccupancyVacant")
  fun handleOccupancyVacant(@RequestBody body: Map<String, Any>) {
    proxy.onOccupancyVacant()
  }

  // User control events
  @Topic(name = "userLevelLight", pubsubName = "pubsub")
  @PostMapping("/userLevelLight")
  fun handleUserLevelLight(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val lightLevel = (data["lightLevel"] as? Number)?.toInt() ?: 0
    proxy.onActivateLightUserLevel(lightLevel)
  }

  @Topic(name = "occupancyLight", pubsubName = "pubsub")
  @PostMapping("/occupancyLight")
  fun handleDeactivateLightUserLevel(@RequestBody body: Map<String, Any>) {
    proxy.onDeactivateLightUserLevel()
  }

  // Energy events
  @Topic(name = "energySavingMode", pubsubName = "pubsub")
  @PostMapping("/energySavingMode")
  fun handleActivateEnergySaving(@RequestBody body: Map<String, Any>) {
    proxy.onActivateEnergySaving()
  }

  @Topic(name = "energyNormalMode", pubsubName = "pubsub")
  @PostMapping("/energyNormalMode")
  fun handleDeactivateEnergySaving(@RequestBody body: Map<String, Any>) {
    proxy.onDeactivateEnergySaving()
  }

  // Safety events
  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val emergencyInRoom = data["emergencyInRoom"] as? String ?: "unknown"
    proxy.onFireAlarm(emergencyInRoom)
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected(@RequestBody body: Map<String, Any>) {
    proxy.onGasLeakDetected()
  }

  @Topic(name = "arcFaultDetected", pubsubName = "pubsub")
  @PostMapping("/arcFaultDetected")
  fun handleArcFaultDetected(@RequestBody body: Map<String, Any>) {
    proxy.onArcFaultDetected()
  }

  // Safety recovery events
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

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault(@RequestBody body: Map<String, Any>) {
    proxy.onResetElectricalFault()
  }

  // Security events
  @Topic(name = "flashAllLights", pubsubName = "pubsub")
  @PostMapping("/flashAllLights")
  fun handleFlashAllLights(@RequestBody body: Map<String, Any>) {
    proxy.onFlashAllLights()
  }

  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody body: Map<String, Any>) {
    proxy.onClearSecurityAlert()
  }
}
