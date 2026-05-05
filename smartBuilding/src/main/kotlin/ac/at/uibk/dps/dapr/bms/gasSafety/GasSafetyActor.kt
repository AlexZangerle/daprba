package ac.at.uibk.dps.dapr.bms.gasSafety

import io.dapr.actors.ActorType

@ActorType(name = "GasSafetyActor")
interface GasSafetyActor {
  fun initialize()

  fun onGasPurged(data: Map<String, Any>)

  fun onPollTimer()
}
