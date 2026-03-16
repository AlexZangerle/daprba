package ac.at.uibk.dps.dapr.bms.buildingschedule

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "buildingSchedule")
class BuildingScheduleSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "buildingSchedule-0"
  private val actorClient = ActorClient()
  private val proxy: BuildingScheduleActor =
    ActorProxyBuilder(BuildingScheduleActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "fireAlarm", pubsubName = "pubsub")
  @PostMapping("/fireAlarm")
  fun handleFireAlarm(@RequestBody body: Map<String, Any>) {
    proxy.onFireAlarm()
  }

  @Topic(name = "gasLeakDetected", pubsubName = "pubsub")
  @PostMapping("/gasLeakDetected")
  fun handleGasLeakDetected(@RequestBody body: Map<String, Any>) {
    proxy.onGasLeakDetected()
  }

  @Topic(name = "disarmFireAlarm", pubsubName = "pubsub")
  @PostMapping("/disarmFireAlarm")
  fun handleDisarmFireAlarm(@RequestBody body: Map<String, Any>) {
    proxy.onDisarmFireAlarm()
  }

  @Topic(name = "gasPurged", pubsubName = "pubsub")
  @PostMapping("/gasPurged")
  fun handleGasPurged(@RequestBody body: Map<String, Any>) {
    proxy.onGasPurged()
  }
}
