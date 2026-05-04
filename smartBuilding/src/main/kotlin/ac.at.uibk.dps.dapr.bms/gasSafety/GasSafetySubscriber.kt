package ac.at.uibk.dps.dapr.bms.gasSafety

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "gasSafety")
class GasSafetySubscriber : ApplicationListener<ApplicationReadyEvent> {

  private val actorId = System.getenv("ACTOR_ID") ?: "gasSafety-0"
  private val proxy: GasSafetyActor =
    ActorProxyBuilder(GasSafetyActor::class.java, ActorClient()).build(ActorId(actorId))

  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    proxy.initialize()
  }

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged() {
    proxy.onGasPurged()
  }
}
