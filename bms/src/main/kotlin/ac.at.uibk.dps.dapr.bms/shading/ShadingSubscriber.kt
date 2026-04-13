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

  // User control events
  @Topic(name = "userLevelBlinds", pubsubName = "pubsub")
  @PostMapping("/userLevelBlinds")
  fun handleUserLevelBlinds(@RequestBody event: CloudEvent<Map<String, Any>>) {
    val blindLevel = (event.data["blindLevel"] as? Number)?.toInt() ?: 0
    proxy.onActivateBlindsUserLevel(blindLevel)
  }

  @Topic(name = "temperatureBlinds", pubsubName = "pubsub")
  @PostMapping("/temperatureBlinds")
  fun handleDeactivateBlindsUserLevel() {
    proxy.onDeactivateBlindsUserLevel()
  }

  @Topic(name = "lockBlinds", pubsubName = "pubsub")
  @PostMapping("/lockBlinds")
  fun handleLockBlinds() {
    proxy.onLockBlinds()
  }

  @Topic(name = "unlockBlinds", pubsubName = "pubsub")
  @PostMapping("/unlockBlinds")
  fun handleUnlockBlinds() {
    proxy.onUnlockBlinds()
  }

  // Zone events
  @Topic(name = "zoneActive", pubsubName = "pubsub")
  @PostMapping("/zoneActive")
  fun handleZoneActive() {
    proxy.onZoneActive()
  }

  @Topic(name = "zoneInactive", pubsubName = "pubsub")
  @PostMapping("/zoneInactive")
  fun handleZoneInactive() {
    proxy.onZoneInactive()
  }

  @Topic(name = "lockdownZone", pubsubName = "pubsub")
  @PostMapping("/lockdownZone")
  fun handleLockdownZone() {
    proxy.onLockdownZone()
  }

  // Safety events
  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm() {
    proxy.onFireAlarm()
  }

  @Topic(name = "smokeAlert", pubsubName = "pubsub")
  @PostMapping("/smokeAlert")
  fun handleSmokeAlert() {
    proxy.onSmokeAlert()
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

  @Topic(name = "disarmSmokeAlert", pubsubName = "pubsub")
  @PostMapping("/disarmSmokeAlert")
  fun handleDisarmSmokeAlert() {
    proxy.onDisarmSmokeAlert()
  }

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault() {
    proxy.onResetElectricalFault()
  }

  // Security events
  @Topic(name = "clearSecurityAlert", pubsubName = "pubsub")
  @PostMapping("/clearSecurityAlert")
  fun handleClearSecurityAlert() {
    proxy.onClearSecurityAlert()
  }
}
