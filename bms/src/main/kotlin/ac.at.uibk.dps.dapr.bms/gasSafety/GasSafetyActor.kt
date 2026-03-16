package ac.at.uibk.dps.dapr.bms.gassafety

import io.dapr.actors.ActorType

/**
 * Gas safety actor interface.
 *
 */
@ActorType(name = "GasSafetyActor")
interface GasSafetyActor {
  fun onGasPurged()
}
