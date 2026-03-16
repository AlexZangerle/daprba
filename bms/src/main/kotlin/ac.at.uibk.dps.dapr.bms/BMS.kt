package ac.at.uibk.dps.dapr.bms

import ac.at.uibk.dps.dapr.bms.lighting.LightingActorImpl
import ac.at.uibk.dps.dapr.bms.hvac.HvacActorImpl
import ac.at.uibk.dps.dapr.bms.shading.ShadingActorImpl
import ac.at.uibk.dps.dapr.bms.roomoccupancy.RoomOccupancyActorImpl
import ac.at.uibk.dps.dapr.bms.fire.FireActorImpl
import ac.at.uibk.dps.dapr.bms.firedoor.FireDoorActorImpl
import io.dapr.actors.runtime.ActorRuntime
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication class BMS

fun main(args: Array<String>) {
  val role = System.getenv("ROLE") ?: "lighting"
  when (role) {
    "lighting" -> ActorRuntime.getInstance().registerActor(LightingActorImpl::class.java)
    "hvac" -> ActorRuntime.getInstance().registerActor(HvacActorImpl::class.java)
    "shading" -> ActorRuntime.getInstance().registerActor(ShadingActorImpl::class.java)
    "roomOccupancy" -> ActorRuntime.getInstance().registerActor(RoomOccupancyActorImpl::class.java)
    "fire" -> ActorRuntime.getInstance().registerActor(FireActorImpl::class.java)
    "fireDoor" -> ActorRuntime.getInstance().registerActor(FireDoorActorImpl::class.java)
  }
  runApplication<BMS>(*args)
}
