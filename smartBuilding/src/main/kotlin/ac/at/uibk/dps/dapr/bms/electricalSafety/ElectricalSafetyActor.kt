package ac.at.uibk.dps.dapr.bms.electricalsafety

import io.dapr.actors.ActorType

@ActorType(name = "ElectricalSafetyActor")
interface ElectricalSafetyActor {
  fun initialize()

  fun onResetElectricalFault(data: Map<String, Any>)

  fun onPollTimer()
}
