package ac.at.uibk.dps.dapr.bms

import ac.at.uibk.dps.dapr.bms.lighting.LightingActorImpl
import io.dapr.actors.runtime.ActorRuntime
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication class LightingApp

fun main(args: Array<String>) {
  val role = System.getenv("ROLE") ?: "lighting"
  when (role) {
    "lighting" -> ActorRuntime.getInstance().registerActor(LightingActorImpl::class.java)
    "hvac" -> ActorRuntime.getInstance().registerActor(HvacActorImpl::class.java)
  runApplication<LightingApp>(*args)
}
