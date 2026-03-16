package ac.at.uibk.dps.dapr.bms.shading

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the shading actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "shading")
class ShadingSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "shading-0"
  private val actorClient = ActorClient()
  private val shadingProxy: ShadingActor =
    ActorProxyBuilder(ShadingActor::class.java, actorClient).build(ActorId(actorId))

  // User control events
  @Topic(name = "userLevelBlinds", pubsubName = "pubsub")
  @PostMapping("/userLevelBlinds")
  fun handleUserLevelBlinds(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val blindLevel = (data["blindLevel"] as? Number)?.toInt() ?: 0
    shadingProxy.onActivateBlindsUserLevel(blindLevel)
  }

  @Topic(name = "temperatureBlinds", pubsubName = "pubsub")
  @PostMapping("/temperatureBlinds")
  fun handleDeactivateBlindsUserLevel(@RequestBody body: Map<String, Any>) {
    shadingProxy.onDeactivateBlindsUserLevel()
  }

  @Topic(name = "lockBlinds", pubsubName = "pubsub")
  @PostMapping("/lockBlinds")
  fun handleLockBlinds(@RequestBody body: Map<String, Any>) {
    shadingProxy.onLockBlinds()
  }

  @Topic(name = "unlockBlinds", pubsubName = "pubsub")
  @PostMapping("/unlockBlinds")
  fun handleUnlockBlinds(@RequestBody body: Map<String, Any>) {
    shadingProxy.onUnlockBlinds()
  }

  // Zone events
  @Topic(name = "zoneActive", pubsubName = "pubsub")
  @PostMapping("/zoneActive")
  fun handleZoneActive(@RequestBody body: Map<String, Any>) {
    shadingProxy.onZoneActive()
  }

  @Topic(name = "zoneInactive", pubsubName = "pubsub")
  @PostMapping("/zoneInactive")
  fun handleZoneInactive(@RequestBody body: Map<String, Any>) {
    shadingProxy.onZoneInactive()
  }

  @Topic(name = "lockdownZone", pubsubName = "pubsub")
  @PostMapping("/lockdownZone")
  fun handleLockdownZone(@RequestBody body: Map<String, Any>) {
    shadingProxy.onLockdownZone()
  }

  // Safety events
  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody body: Map<String, Any>) {
    shadingProxy.onFireAlarm()
  }

  @Topic(name = "smokeAlert", pubsubName = "pubsub")
  @PostMapping("/smokeAlert")
  fun handleSmokeAlert(@RequestBody body: Map<String, Any>) {
    shadingProxy.onSmokeAlert()
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected(@RequestBody body: Map<String, Any>) {
    shadingProxy.onGasLeakDetected()
  }

  @Topic(name = "arcFaultDetected", pubsubName = "pubsub")
  @PostMapping("/arcFaultDetected")
  fun handleArcFaultDetected(@RequestBody body: Map<String, Any>) {
    shadingProxy.onArcFaultDetected()
  }

  // Safety recovery events
  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody body: Map<String, Any>) {
    shadingProxy.onDisarmFireAlarm()
  }

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged(@RequestBody body: Map<String, Any>) {
    shadingProxy.onGasPurged()
  }

  @Topic(name = "disarmSmokeAlert", pubsubName = "pubsub")
  @PostMapping("/disarmSmokeAlert")
  fun handleDisarmSmokeAlert(@RequestBody body: Map<String, Any>) {
    shadingProxy.onDisarmSmokeAlert()
  }

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault(@RequestBody body: Map<String, Any>) {
    shadingProxy.onResetElectricalFault()
  }

  // Security events
  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert(@RequestBody body: Map<String, Any>) {
    shadingProxy.onClearSecurityAlert()
  }
}
