package ac.at.uibk.dps.dapr.bms.fire

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the fire detection actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "fire")
class FireSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "fire-0"
  private val actorClient = ActorClient()
  private val fireProxy: FireActor =
    ActorProxyBuilder(FireActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "sensorFireDataReceived", pubsubName = "pubsub")
  @PostMapping("/sensorFireDataReceived")
  fun handleSensorFireData(@RequestBody body: Map<String, Any>) {
    val data = body["data"] as? Map<*, *> ?: body
    val imageData = data["imageData"] as? String ?: ""
    val zoneId = data["zoneId"] as? String ?: "Room 0"
    fireProxy.onSensorFireDataReceived(imageData, zoneId)
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody body: Map<String, Any>) {
    fireProxy.onDisarmFireAlarm()
  }

  @Topic(name = "disarmSmokeAlert", pubsubName = "pubsub")
  @PostMapping("/disarmSmokeAlert")
  fun handleDisarmSmokeAlert(@RequestBody body: Map<String, Any>) {
    fireProxy.onDisarmSmokeAlert()
  }
}
