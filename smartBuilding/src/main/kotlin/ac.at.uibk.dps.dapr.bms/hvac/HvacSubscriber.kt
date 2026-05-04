package ac.at.uibk.dps.dapr.bms.hvac

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the HVAC actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "hvac")
class HvacSubscriber : ApplicationListener<ApplicationReadyEvent> {

  private val actorId = System.getenv("ACTOR_ID") ?: "hvac-0"
  private val proxy: HvacActor =
    ActorProxyBuilder(HvacActor::class.java, ActorClient()).build(ActorId(actorId))

  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    proxy.initialize()
  }

  // Occupancy events
  @Topic(name = "occupancyDetectedEvent", pubsubName = "pubsub")
  @PostMapping("/occupancyDetectedEvent")
  fun handleOccupancyDetected() {
    proxy.onOccupancyDetected()
  }

  @Topic(name = "eOccupancyVacant", pubsubName = "pubsub")
  @PostMapping("/eOccupancyVacant")
  fun handleOccupancyVacant() {
    proxy.onOccupancyVacant()
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

  // Schedule events
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

  // Energy events
  @Topic(name = "energySavingMode", pubsubName = "pubsub")
  @PostMapping("/energySavingMode")
  fun handleActivateEnergySaving() {
    proxy.onActivateEnergySaving()
  }

  @Topic(name = "drasticEnergySaving", pubsubName = "pubsub")
  @PostMapping("/drasticEnergySaving")
  fun handleDrasticEnergySaving() {
    proxy.onDrasticEnergySaving()
  }

  @Topic(name = "energyNormalMode", pubsubName = "pubsub")
  @PostMapping("/energyNormalMode")
  fun handleDeactivateEnergySaving() {
    proxy.onDeactivateEnergySaving()
  }

  @Topic(name = "energyShutdown", pubsubName = "pubsub")
  @PostMapping("/energyShutdown")
  fun handleEnergyShutdown() {
    proxy.onEnergyShutdown()
  }

  // Safety events
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

  @Topic(name = "smokeAlert", pubsubName = "pubsub")
  @PostMapping("/smokeAlert")
  fun handleSmokeAlert() {
    proxy.onSmokeAlert()
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
}
