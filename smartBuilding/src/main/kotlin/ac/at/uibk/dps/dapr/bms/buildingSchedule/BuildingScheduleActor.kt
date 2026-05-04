package ac.at.uibk.dps.dapr.bms.buildingSchedule

import io.dapr.actors.ActorType

@ActorType(name = "BuildingScheduleActor")
interface BuildingScheduleActor {
  fun initialize()

  fun onFireAlarm()

  fun onGasLeakDetected()

  fun onDisarmFireAlarm()

  fun onGasPurged()

  fun onScheduleTimer()
}
