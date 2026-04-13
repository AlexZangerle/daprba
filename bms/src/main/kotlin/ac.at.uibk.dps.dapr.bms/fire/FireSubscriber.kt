package ac.at.uibk.dps.dapr.bms.fire

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import io.dapr.client.domain.CloudEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the fire detection actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "fire")
class FireSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "fire-0"
  private val fireProxy: FireActor =
    ActorProxyBuilder(FireActor::class.java, ActorClient()).build(ActorId(actorId))

  @Topic(name = "sensorFireDataReceived", pubsubName = "pubsub")
  @PostMapping("/sensorFireDataReceived")
  fun handleSensorFireData(@RequestBody event: CloudEvent<Map<String, Any>>) {
    val imageData = event.data["imageData"] as? String ?: ""
    val zoneId = event.data["zoneId"] as? String ?: "Room 0"
    fireProxy.onSensorFireDataReceived(mapOf("imageData" to imageData, "zoneId" to zoneId))
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm() {
    fireProxy.onDisarmFireAlarm()
  }

  @Topic(name = "disarmSmokeAlert", pubsubName = "pubsub")
  @PostMapping("/disarmSmokeAlert")
  fun handleDisarmSmokeAlert() {
    fireProxy.onDisarmSmokeAlert()
  }
}
