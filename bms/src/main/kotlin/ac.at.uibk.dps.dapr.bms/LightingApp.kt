package at.ac.uibk.dps.dapr.lighting

import io.dapr.actors.runtime.ActorRuntime
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication class LightingApp

fun main(args: Array<String>) {
  ActorRuntime.getInstance().registerActor(LightingActorImpl::class.java)
  runApplication<LightingApp>(*args)
}
