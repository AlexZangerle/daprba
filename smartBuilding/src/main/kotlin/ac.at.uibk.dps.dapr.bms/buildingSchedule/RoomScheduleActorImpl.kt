package ac.at.uibk.dps.dapr.bms.buildingSchedule

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class RoomScheduleActorImpl(
  runtimeContext: ActorRuntimeContext<RoomScheduleActorImpl>,
  actorId: ActorId,
) : AbstractActor(runtimeContext, actorId), RoomScheduleActor {

  enum class State {
    INIT,
    ACTIVE,
    INACTIVE,
    WARNING,
    AUTHORIZED_STAY,
    EMERGENCY,
  }

  private var state: State = State.INIT
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false
  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var timer: ScheduledFuture<*>? = null

  override fun initialize() {
    state = State.INIT
    service.initializeZone { enterInactive() }
  }

  private fun cancelTimer() {
    timer?.cancel(false)
    timer = null
  }

  private fun enterActive() {
    state = State.ACTIVE
    cancelTimer()
    daprClient.publishEvent("pubsub", "zoneActive", mapOf<String, Any>()).subscribe()
  }

  private fun enterInactive() {
    state = State.INACTIVE
    cancelTimer()
    daprClient.publishEvent("pubsub", "zoneInactive", mapOf<String, Any>()).subscribe()
  }

  private fun enterWarning() {
    state = State.WARNING
    cancelTimer()
    daprClient.publishEvent("pubsub", "zoneWarning", mapOf<String, Any>()).subscribe()
    timer =
      scheduler.schedule(
        { if (state == State.WARNING) enterInactive() },
        300000,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun enterAuthorizedStay() {
    state = State.AUTHORIZED_STAY
    cancelTimer()
    daprClient.publishEvent("pubsub", "zoneActive", mapOf<String, Any>()).subscribe()
    timer =
      scheduler.schedule(
        { if (state == State.AUTHORIZED_STAY) enterWarning() },
        7200000,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelTimer()
  }

  override fun onEnterBusinessHours() {
    if (state == State.INACTIVE || state == State.WARNING || state == State.AUTHORIZED_STAY)
      enterActive()
  }

  override fun onEnterAfterHours() {
    if (state == State.ACTIVE) enterWarning()
  }

  override fun onEnterWeekend() {
    if (state == State.ACTIVE) enterWarning()
  }

  override fun onRequestStay() {
    if (state == State.INACTIVE || state == State.WARNING) enterAuthorizedStay()
  }

  override fun onUserLeftZone() {
    if (state == State.AUTHORIZED_STAY) enterInactive()
  }

  override fun onFireAlarm() {
    fireAlarm = true
    if (
      state == State.ACTIVE ||
        state == State.INACTIVE ||
        state == State.WARNING ||
        state == State.AUTHORIZED_STAY
    )
      enterEmergency()
  }

  override fun onGasLeakDetected() {
    gasActive = true
    if (
      state == State.ACTIVE ||
        state == State.INACTIVE ||
        state == State.WARNING ||
        state == State.AUTHORIZED_STAY
    )
      enterEmergency()
  }

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    if (state == State.EMERGENCY && !fireAlarm && !gasActive) enterInactive()
  }

  override fun onGasPurged() {
    gasActive = false
    if (state == State.EMERGENCY && !fireAlarm && !gasActive) enterInactive()
  }
}
