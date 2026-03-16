package ac.at.uibk.dps.dapr.bms.electricalsafety

import io.dapr.actors.ActorType

/**
 * Electrical safety actor interface.
 *
 */
@ActorType(name = "ElectricalSafetyActor")
interface ElectricalSafetyActor {
  fun onResetElectricalFault()
}
