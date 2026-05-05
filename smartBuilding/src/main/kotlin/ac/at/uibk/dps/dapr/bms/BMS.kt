package ac.at.uibk.dps.dapr.bms

import ac.at.uibk.dps.dapr.bms.buildingSchedule.BuildingScheduleActorImpl
import ac.at.uibk.dps.dapr.bms.buildingSchedule.RoomScheduleActorImpl
import ac.at.uibk.dps.dapr.bms.electricalSafety.ElectricalSafetyActorImpl
import ac.at.uibk.dps.dapr.bms.energyManagement.EnergyManagementActorImpl
import ac.at.uibk.dps.dapr.bms.fire.FireActorImpl
import ac.at.uibk.dps.dapr.bms.fire.FireDoorActorImpl
import ac.at.uibk.dps.dapr.bms.gasSafety.GasSafetyActorImpl
import ac.at.uibk.dps.dapr.bms.hvac.HvacActorImpl
import ac.at.uibk.dps.dapr.bms.lighting.LightingActorImpl
import ac.at.uibk.dps.dapr.bms.roomOccupancy.RoomOccupancyActorImpl
import ac.at.uibk.dps.dapr.bms.security.AccessControlActorImpl
import ac.at.uibk.dps.dapr.bms.security.SecurityManagerActorImpl
import ac.at.uibk.dps.dapr.bms.shading.ShadingActorImpl
import ac.at.uibk.dps.dapr.bms.tempSafety.TempSafetyActorImpl
import com.codahale.metrics.CsvReporter
import com.codahale.metrics.MetricRegistry
import io.dapr.actors.runtime.ActorRuntime
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.dropwizard.DropwizardConfig
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry
import io.micrometer.core.instrument.util.HierarchicalNameMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

@SpringBootApplication
class BMS {
  companion object {
    val metricsDirectory = System.getenv("METRICS_DIRECTORY") ?: "metrics"
    val metricsRegistry =
      MetricRegistry().apply {
        val path = Paths.get(metricsDirectory).toAbsolutePath()
        Files.createDirectories(path)
        CsvReporter.forRegistry(this).build(path.toFile()).start(1L, TimeUnit.SECONDS)
        object :
            DropwizardMeterRegistry(
              object : DropwizardConfig {
                override fun get(key: String): String? = null

                override fun prefix(): String = ""
              },
              this,
              HierarchicalNameMapper.DEFAULT,
              Clock.SYSTEM,
            ) {
            override fun nullGaugeValue(): Double = Double.NaN
          }
          .apply {
            ProcessorMetrics().bindTo(this)
            JvmMemoryMetrics().bindTo(this)
            JvmGcMetrics().bindTo(this)
          }
      }
  }
}

fun main(args: Array<String>) {
  val role = System.getenv("ROLE") ?: "lighting"
  when (role) {
    "lighting" -> ActorRuntime.getInstance().registerActor(LightingActorImpl::class.java)
    "hvac" -> ActorRuntime.getInstance().registerActor(HvacActorImpl::class.java)
    "shading" -> ActorRuntime.getInstance().registerActor(ShadingActorImpl::class.java)
    "roomOccupancy" -> ActorRuntime.getInstance().registerActor(RoomOccupancyActorImpl::class.java)
    "fire" -> ActorRuntime.getInstance().registerActor(FireActorImpl::class.java)
    "fireDoor" -> ActorRuntime.getInstance().registerActor(FireDoorActorImpl::class.java)
    "electricalSafety" ->
      ActorRuntime.getInstance().registerActor(ElectricalSafetyActorImpl::class.java)
    "gasSafety" -> ActorRuntime.getInstance().registerActor(GasSafetyActorImpl::class.java)
    "tempSafety" -> ActorRuntime.getInstance().registerActor(TempSafetyActorImpl::class.java)
    "buildingSchedule" ->
      ActorRuntime.getInstance().registerActor(BuildingScheduleActorImpl::class.java)
    "roomSchedule" -> ActorRuntime.getInstance().registerActor(RoomScheduleActorImpl::class.java)
    "energyManagement" ->
      ActorRuntime.getInstance().registerActor(EnergyManagementActorImpl::class.java)
    "securityManager" ->
      ActorRuntime.getInstance().registerActor(SecurityManagerActorImpl::class.java)
    "accessControl" -> ActorRuntime.getInstance().registerActor(AccessControlActorImpl::class.java)
  }
  runApplication<BMS>(*args)
}
