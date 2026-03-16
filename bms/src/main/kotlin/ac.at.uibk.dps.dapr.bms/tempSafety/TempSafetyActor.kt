package ac.at.uibk.dps.dapr.bms.tempsafety

import io.dapr.actors.ActorType

/**
 * Temperature safety actor interface.
 *
 */
@ActorType(name = "TempSafetyActor")
interface TempSafetyActor {
  fun onTempRiskCleared()
}
