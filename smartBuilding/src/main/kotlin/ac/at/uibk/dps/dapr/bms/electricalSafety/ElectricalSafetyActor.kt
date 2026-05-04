package ac.at.uibk.dps.dapr.bms.electricalsafety

import io.dapr.actors.ActorType

@ActorType(name = "ElectricalSafetyActor")
interface ElectricalSafetyActor {
  fun initialize()

  fun onResetElectricalFault()

  fun onPollTimer()
}
