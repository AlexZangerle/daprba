package ac.at.uibk.dps.dapr.bms.gasSafety

import io.dapr.actors.ActorType

/** Gas safety actor interface. */
@ActorType(name = "GasSafetyActor")
interface GasSafetyActor {
  fun initialize()

  fun onGasPurged()
}
