package ac.at.uibk.dps.dapr.bms.electricalsafety

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "electricalSafety")
class ElectricalSafetySubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "electricalSafety-0"
  private val actorClient = ActorClient()
  private val proxy: ElectricalSafetyActor =
    ActorProxyBuilder(ElectricalSafetyActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "electricalReset", pubsubName = "pubsub")
  @PostMapping("/electricalReset")
  fun handleResetElectricalFault(@RequestBody body: Map<String, Any>) {
    proxy.onResetElectricalFault()
  }
}
