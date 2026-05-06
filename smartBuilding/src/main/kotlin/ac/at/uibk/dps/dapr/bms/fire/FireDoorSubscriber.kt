package ac.at.uibk.dps.dapr.bms.fire

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

/** Pub/sub subscriber for the fire door actor. */
@RestController
@ConditionalOnProperty("app.role", havingValue = "fireDoor")
class FireDoorSubscriber : ApplicationListener<ApplicationReadyEvent> {

  private val doorIds = (System.getenv("DOOR_IDS") ?: "0").split(",").map { it.trim() }

  private val proxies: List<FireDoorActor> = doorIds.map { id ->
    ActorProxyBuilder(FireDoorActor::class.java, ActorClient())
      .build(ActorId("fireDoor-$id"))
  }

  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    proxies.forEach { it.initialize() }
  }

  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxies.forEach { it.onFireAlarm(event.data) }
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody event: CloudEvent<Map<String, Any>>) {
    proxies.forEach { it.onDisarmFireAlarm(event.data) }
  }
}
