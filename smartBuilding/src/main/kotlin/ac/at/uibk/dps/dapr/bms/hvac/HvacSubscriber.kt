package ac.at.uibk.dps.dapr.bms.hvac

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

  @Topic(name = "occupancyDetected", pubsubName = "pubsub")
  @PostMapping("/occupancyDetected")
  fun handleOccupancyDetected(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onOccupancyDetected(event.data)
  }

  @Topic(name = "occupancyVacant", pubsubName = "pubsub")
  @PostMapping("/occupancyVacant")
  fun handloccupancyVacant(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onOccupancyVacant(event.data)
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

  @Topic(name = "enterBusinessHours", pubsubName = "pubsub")
  @PostMapping("/enterBusinessHours")
  fun handleEnterBusinessHours(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onEnterBusinessHours(event.data)
  }

  @Topic(name = "enterAfterHours", pubsubName = "pubsub")
  @PostMapping("/enterAfterHours")
  fun handleEnterAfterHours(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onEnterAfterHours(event.data)
  }

  @Topic(name = "enterWeekend", pubsubName = "pubsub")
  @PostMapping("/enterWeekend")
  fun handleEnterWeekend(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onEnterWeekend(event.data)
  }

  @Topic(name = "energySaving", pubsubName = "pubsub")
  @PostMapping("/energySaving")
  fun handleActivateEnergySaving(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onActivateEnergySaving(event.data)
  }

  @Topic(name = "drasticEnergySaving", pubsubName = "pubsub")
  @PostMapping("/drasticEnergySaving")
  fun handleDrasticEnergySaving(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDrasticEnergySaving(event.data)
  }

  @Topic(name = "deactivateEnergySaving", pubsubName = "pubsub")
  @PostMapping("/deactivateEnergySaving")
  fun handleDeactivateEnergySaving(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onDeactivateEnergySaving(event.data)
  }

  @Topic(name = "energyShutdown", pubsubName = "pubsub")
  @PostMapping("/energyShutdown")
  fun handleEnergyShutdown(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onEnergyShutdown(event.data)
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

  @Topic(name = "smokeAlert", pubsubName = "pubsub")
  @PostMapping("/smokeAlert")
  fun handleSmokeAlert(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onSmokeAlert(event.data)
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
}
