package ac.at.uibk.dps.dapr.bms.roomschedule

import io.dapr.Topic
import io.dapr.actors.ActorId
import io.dapr.actors.client.ActorClient
import io.dapr.actors.client.ActorProxyBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty("app.role", havingValue = "roomSchedule")
class RoomScheduleSubscriber {

  private val actorId = System.getenv("ACTOR_ID") ?: "roomSchedule-0"
  private val actorClient = ActorClient()
  private val proxy: RoomScheduleActor =
    ActorProxyBuilder(RoomScheduleActor::class.java, actorClient).build(ActorId(actorId))

  @Topic(name = "enterBusinessHours", pubsubName = "pubsub")
  @PostMapping("/enterBusinessHours")
  fun handleEnterBusinessHours(@RequestBody body: Map<String, Any>) {
    proxy.onEnterBusinessHours()
  }

  @Topic(name = "enterAfterHours", pubsubName = "pubsub")
  @PostMapping("/enterAfterHours")
  fun handleEnterAfterHours(@RequestBody body: Map<String, Any>) {
    proxy.onEnterAfterHours()
  }

  @Topic(name = "enterWeekend", pubsubName = "pubsub")
  @PostMapping("/enterWeekend")
  fun handleEnterWeekend(@RequestBody body: Map<String, Any>) {
    proxy.onEnterWeekend()
  }

  @Topic(name = "requestStay", pubsubName = "pubsub")
  @PostMapping("/requestStay")
  fun handleRequestStay(@RequestBody body: Map<String, Any>) {
    proxy.onRequestStay()
  }

  @Topic(name = "userLeftZone", pubsubName = "pubsub")
  @PostMapping("/userLeftZone")
  fun handleUserLeftZone(@RequestBody body: Map<String, Any>) {
    proxy.onUserLeftZone()
  }

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
