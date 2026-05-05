package ac.at.uibk.dps.dapr.bms.buildingSchedule

import io.dapr.actors.ActorType

@ActorType(name = "BuildingScheduleActor")
interface BuildingScheduleActor {
  fun initialize()

  fun onFireAlarm(data: Map<String, Any>)

  fun onGasLeakDetected(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onGasPurged(data: Map<String, Any>)

  fun onScheduleTimer()
}
