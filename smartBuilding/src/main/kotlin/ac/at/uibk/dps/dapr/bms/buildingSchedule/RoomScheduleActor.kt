package ac.at.uibk.dps.dapr.bms.buildingSchedule

import io.dapr.actors.ActorType

@ActorType(name = "RoomScheduleActor")
interface RoomScheduleActor {
  fun initialize()

  fun onEnterBusinessHours(data: Map<String, Any>)

  fun onEnterAfterHours(data: Map<String, Any>)

  fun onEnterWeekend(data: Map<String, Any>)

  fun onRequestStay(data: Map<String, Any>)

  fun onUserLeftZone(data: Map<String, Any>)

  fun onFireAlarm(data: Map<String, Any>)

  fun onGasLeakDetected(data: Map<String, Any>)

  fun onDisarmFireAlarm(data: Map<String, Any>)

  fun onGasPurged(data: Map<String, Any>)

  fun onRoomTimer()
}
