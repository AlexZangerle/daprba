package ac.at.uibk.dps.dapr.bms.firedoor

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Pub/sub subscriber for the fire door actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "fireDoor")
class FireDoorSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "fireDoor-0"
  private val actorClient = ActorClient()
  private val fireDoorProxy: FireDoorActor =
    ActorProxyBuilder(FireDoorActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody body: Map<String, Any>) {
    fireDoorProxy.onFireAlarm()
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody body: Map<String, Any>) {
    fireDoorProxy.onDisarmFireAlarm()
  }
}
