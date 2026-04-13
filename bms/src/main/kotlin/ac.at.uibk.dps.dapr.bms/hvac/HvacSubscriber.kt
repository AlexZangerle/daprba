package ac.at.uibk.dps.dapr.bms.hvac

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the HVAC actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "hvac")
class HvacSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "hvac-0"
  private val actorClient = ActorClient()
  private val proxy: HvacActor =
    ActorProxyBuilder(HvacActor::class.java, actorClient).build(ActorId(actorId))

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

  @Topic(name = "eOccupancyVacant", pubsubName = "pubsub")
  @PostMapping("/eOccupancyVacant")
  fun handleOccupancyVacant(@RequestBody body: Map<String, Any>) {
    proxy.onOccupancyVacant()
  }

  // Zone events
  @Topic(name = "zoneActive", pubsubName = "pubsub")
  @PostMapping("/zoneActive")
  fun handleZoneActive(@RequestBody body: Map<String, Any>) {
    proxy.onZoneActive()
  }

  @Topic(name = "zoneInactive", pubsubName = "pubsub")
  @PostMapping("/zoneInactive")
  fun handleZoneInactive(@RequestBody body: Map<String, Any>) {
    proxy.onZoneInactive()
  }

  // Schedule events
  @Topic(name = "enterBusinessHours", pubsubName = "pubsub")
  @PostMapping("/enterBusinessHours")
  fun handleEnterBusinessHours(@RequestBody body: Map<String, Any>) {
    proxy.onEnterBusinessHours()
  }

  @Topic(name = "enterAfterHours", pubsubName = "pubsub")
  @PostMapping("/enterAfterHours")
  fun handleEnterAfterHours(@RequestBody body: Map<String, Any>) {
    proxy.onEnterAfterHours()
  }

  @Topic(name = "enterWeekend", pubsubName = "pubsub")
  @PostMapping("/enterWeekend")
  fun handleEnterWeekend(@RequestBody body: Map<String, Any>) {
    proxy.onEnterWeekend()
  }

  // Energy events
  @Topic(name = "energySavingMode", pubsubName = "pubsub")
  @PostMapping("/energySavingMode")
  fun handleActivateEnergySaving(@RequestBody body: Map<String, Any>) {
    proxy.onActivateEnergySaving()
  }

  @Topic(name = "drasticEnergySaving", pubsubName = "pubsub")
  @PostMapping("/drasticEnergySaving")
  fun handleDrasticEnergySaving(@RequestBody body: Map<String, Any>) {
    proxy.onDrasticEnergySaving()
  }

  @Topic(name = "energyNormalMode", pubsubName = "pubsub")
  @PostMapping("/energyNormalMode")
  fun handleDeactivateEnergySaving(@RequestBody body: Map<String, Any>) {
    proxy.onDeactivateEnergySaving()
  }

  @Topic(name = "energyShutdown", pubsubName = "pubsub")
  @PostMapping("/energyShutdown")
  fun handleEnergyShutdown(@RequestBody body: Map<String, Any>) {
    proxy.onEnergyShutdown()
  }

  // Safety events
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

  @Topic(name = "smokeAlert", pubsubName = "pubsub")
  @PostMapping("/smokeAlert")
  fun handleSmokeAlert(@RequestBody body: Map<String, Any>) {
    proxy.onSmokeAlert()
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

  @Topic(name = "disarmSmokeAlert", pubsubName = "pubsub")
  @PostMapping("/disarmSmokeAlert")
  fun handleDisarmSmokeAlert(@RequestBody body: Map<String, Any>) {
    proxy.onDisarmSmokeAlert()
  }

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault(@RequestBody body: Map<String, Any>) {
    proxy.onResetElectricalFault()
  }
}
