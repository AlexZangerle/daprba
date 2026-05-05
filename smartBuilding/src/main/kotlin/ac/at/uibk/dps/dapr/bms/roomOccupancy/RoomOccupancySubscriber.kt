package ac.at.uibk.dps.dapr.bms.roomOccupancy

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import io.dapr.client.domain.CloudEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the room occupancy actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "roomOccupancy")
class RoomOccupancySubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "roomOccupancy-0"
  private val proxy: RoomOccupancyActor =
    ActorProxyBuilder(RoomOccupancyActor::class.java, ActorClient()).build(ActorId(actorId))

  @Topic(name = "sensorOccupancyReceived", pubsubName = "pubsub")
  @PostMapping("/sensorOccupancyReceived")
  fun handleSensorData(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onSensorOccupancyReceived(event.data)
  }

  @Topic(name = "energySavingMode", pubsubName = "pubsub")
  @PostMapping("/energySavingMode")
  fun handleEnergySaving(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onActivateEnergySaving(event.data)
  }

  @Topic(name = "maintenanceDone", pubsubName = "pubsub")
  @PostMapping("/maintenanceDone")
  fun handleMaintenanceDone(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxy.onMaintenanceDone(event.data)
  }
}
