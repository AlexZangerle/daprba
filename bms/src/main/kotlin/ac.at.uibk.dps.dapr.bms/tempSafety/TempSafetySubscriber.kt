package ac.at.uibk.dps.dapr.bms.tempsafety

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "tempSafety")
class TempSafetySubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "tempSafety-0"
  private val actorClient = ActorClient()
  private val proxy: TempSafetyActor =
    ActorProxyBuilder(TempSafetyActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "tempRiskCleared", pubsubName = "pubsub")
  @PostMapping("/tempRiskCleared")
  fun handleTempRiskCleared(@RequestBody body: Map<String, Any>) {
    proxy.onTempRiskCleared()
  }
}
