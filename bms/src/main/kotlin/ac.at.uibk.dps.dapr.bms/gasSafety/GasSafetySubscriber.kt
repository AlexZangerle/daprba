package ac.at.uibk.dps.dapr.bms.gassafety

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "gasSafety")
class GasSafetySubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "gasSafety-0"
  private val actorClient = ActorClient()
  private val proxy: GasSafetyActor =
    ActorProxyBuilder(GasSafetyActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged(@RequestBody body: Map<String, Any>) {
    proxy.onGasPurged()
  }
}
