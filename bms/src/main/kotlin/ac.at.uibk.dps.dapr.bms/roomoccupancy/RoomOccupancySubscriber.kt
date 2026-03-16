package ac.at.uibk.dps.dapr.bms.roomoccupancy

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the room occupancy actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "roomOccupancy")
class RoomOccupancySubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "roomOccupancy-0"
  private val actorClient = ActorClient()
  private val proxy: RoomOccupancyActor =
    ActorProxyBuilder(RoomOccupancyActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "sensorOccupancyReceived", pubsubName = "pubsub")
  @PostMapping("/sensorOccupancyReceived")
  fun handleSensorData(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val imageData = data["imageData"] as? String ?: ""
    proxy.onSensorOccupancyReceived(imageData)
  }

  @Topic(name = "energySavingMode", pubsubName = "pubsub")
  @PostMapping("/energySavingMode")
  fun handleEnergySaving(@RequestBody body: Map<String, Any>) {
    proxy.onActivateEnergySaving()
  }

  @Topic(name = "maintenanceDone", pubsubName = "pubsub")
  @PostMapping("/maintenanceDone")
  fun handleMaintenanceDone(@RequestBody body: Map<String, Any>) {
    proxy.onMaintenanceDone()
  }
}
