package ac.at.uibk.dps.dapr.bms.tempSafety

import io.dapr.actors.ActorType

@ActorType(name = "TempSafetyActor")
interface TempSafetyActor {
  fun initialize()

  fun onTempRiskCleared()

  fun onPollTimer()
}
