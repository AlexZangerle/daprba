package ac.at.uibk.dps.dapr.bms.roomSchedule

import io.dapr.actors.ActorType

/** Room schedule actor interface. */
@ActorType(name = "RoomScheduleActor")
interface RoomScheduleActor {
  fun initialize()

  fun onEnterBusinessHours()

  fun onEnterAfterHours()

  fun onEnterWeekend()

  fun onRequestStay()

  fun onUserLeftZone()

  fun onFireAlarm()

  fun onGasLeakDetected()

  fun onDisarmFireAlarm()

  fun onGasPurged()
}
