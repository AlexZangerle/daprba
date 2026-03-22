package ac.at.uibk.dps.dapr.bms.hvac

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
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
  private val hvacProxy: HvacActor =
    ActorProxyBuilder(HvacActor::class.java, actorClient).build(ActorId(actorId))

  // Occupancy events
  @Topic(name = "occupancyDetectedEvent", pubsubName = "pubsub")
  @PostMapping("/occupancyDetectedEvent")
  fun handleOccupancyDetected(@RequestBody body: Map<String, Any>) {
    hvacProxy.onOccupancyDetected()
  }

  @Topic(name = "eOccupancyVacant", pubsubName = "pubsub")
  @PostMapping("/eOccupancyVacant")
  fun handleOccupancyVacant(@RequestBody body: Map<String, Any>) {
    hvacProxy.onOccupancyVacant()
  }

  // Zone events
  @Topic(name = "zoneActive", pubsubName = "pubsub")
  @PostMapping("/zoneActive")
  fun handleZoneActive(@RequestBody body: Map<String, Any>) {
    hvacProxy.onZoneActive()
  }

  @Topic(name = "zoneInactive", pubsubName = "pubsub")
  @PostMapping("/zoneInactive")
  fun handleZoneInactive(@RequestBody body: Map<String, Any>) {
    hvacProxy.onZoneInactive()
  }

  // Schedule events
  @Topic(name = "enterBusinessHours", pubsubName = "pubsub")
  @PostMapping("/enterBusinessHours")
  fun handleEnterBusinessHours(@RequestBody body: Map<String, Any>) {
    hvacProxy.onEnterBusinessHours()
  }

  @Topic(name = "enterAfterHours", pubsubName = "pubsub")
  @PostMapping("/enterAfterHours")
  fun handleEnterAfterHours(@RequestBody body: Map<String, Any>) {
    hvacProxy.onEnterAfterHours()
  }

  @Topic(name = "enterWeekend", pubsubName = "pubsub")
  @PostMapping("/enterWeekend")
  fun handleEnterWeekend(@RequestBody body: Map<String, Any>) {
    hvacProxy.onEnterWeekend()
  }

  // Energy events
  @Topic(name = "energySavingMode", pubsubName = "pubsub")
  @PostMapping("/energySavingMode")
  fun handleActivateEnergySaving(@RequestBody body: Map<String, Any>) {
    hvacProxy.onActivateEnergySaving()
  }

  @Topic(name = "drasticEnergySaving", pubsubName = "pubsub")
  @PostMapping("/drasticEnergySaving")
  fun handleDrasticEnergySaving(@RequestBody body: Map<String, Any>) {
    hvacProxy.onDrasticEnergySaving()
  }

  @Topic(name = "energyNormalMode", pubsubName = "pubsub")
  @PostMapping("/energyNormalMode")
  fun handleDeactivateEnergySaving(@RequestBody body: Map<String, Any>) {
    hvacProxy.onDeactivateEnergySaving()
  }

  @Topic(name = "energyShutdown", pubsubName = "pubsub")
  @PostMapping("/energyShutdown")
  fun handleEnergyShutdown(@RequestBody body: Map<String, Any>) {
    hvacProxy.onEnergyShutdown()
  }

  // Safety events
  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody body: Map<String, Any>) {
    hvacProxy.onFireAlarm()
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected(@RequestBody body: Map<String, Any>) {
    hvacProxy.onGasLeakDetected()
  }

  @Topic(name = "smokeAlert", pubsubName = "pubsub")
  @PostMapping("/smokeAlert")
  fun handleSmokeAlert(@RequestBody body: Map<String, Any>) {
    hvacProxy.onSmokeAlert()
  }

  @Topic(name = "arcFaultDetected", pubsubName = "pubsub")
  @PostMapping("/arcFaultDetected")
  fun handleArcFaultDetected(@RequestBody body: Map<String, Any>) {
    hvacProxy.onArcFaultDetected()
  }

  // Safety recovery events
  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody body: Map<String, Any>) {
    hvacProxy.onDisarmFireAlarm()
  }

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged(@RequestBody body: Map<String, Any>) {
    hvacProxy.onGasPurged()
  }

  @Topic(name = "disarmSmokeAlert", pubsubName = "pubsub")
  @PostMapping("/disarmSmokeAlert")
  fun handleDisarmSmokeAlert(@RequestBody body: Map<String, Any>) {
    hvacProxy.onDisarmSmokeAlert()
  }

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault(@RequestBody body: Map<String, Any>) {
    hvacProxy.onResetElectricalFault()
  }
}
