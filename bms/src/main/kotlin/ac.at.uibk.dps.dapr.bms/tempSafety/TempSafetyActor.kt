package ac.at.uibk.dps.dapr.bms.tempSafety

import io.dapr.actors.ActorType

/** Temperature safety actor interface. */
@ActorType(name = "TempSafetyActor")
interface TempSafetyActor {
  fun initialize()

  fun onTempRiskCleared()
}
