package ac.at.uibk.dps.dapr.bms.roomschedule

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Room schedule actor implementation.
 *
 * States: INIT, ACTIVE, INACTIVE, WARNING, AUTHORIZED_STAY, EMERGENCY
 */
class RoomScheduleActorImpl(
  runtimeContext: ActorRuntimeContext<RoomScheduleActorImpl>,
  actorId: ActorId
) : AbstractActor(runtimeContext, actorId), RoomScheduleActor {

  enum class State { INIT, ACTIVE, INACTIVE, WARNING, AUTHORIZED_STAY, EMERGENCY }

  private var state: State = State.INIT

  // Persistent
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false

  private val daprClient = DaprClientBuilder().build()
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var timer: ScheduledFuture<*>? = null

  init {
    enterInit()
    println("[ROOM_SCHEDULE] Initialized")
  }

  // Pub/sub emission
  private fun emitZoneActive() {
    daprClient.publishEvent("pubsub", "zoneActive", mapOf<String, Any>()).subscribe()
    println("[ROOM_SCHEDULE] EMITTED zoneActive")
  }

  private fun emitZoneInactive() {
    daprClient.publishEvent("pubsub", "zoneInactive", mapOf<String, Any>()).subscribe()
    println("[ROOM_SCHEDULE] EMITTED zoneInactive")
  }

  private fun emitZoneWarning() {
    daprClient.publishEvent("pubsub", "zoneWarning", mapOf<String, Any>()).subscribe()
    println("[ROOM_SCHEDULE] EMITTED zoneWarning")
  }

  // Timer
  private fun cancelTimer() {
    timer?.cancel(false)
    timer = null
  }

  // Guard
  private fun isSafeToExitEmergency(): Boolean = !fireAlarm && !gasActive

  // State entries
  private fun enterInit() {
    state = State.INIT
    // CSML: entry invokes initializeZone -> zoneInitialized -> inactive
    service.initializeZone { enterInactive() }
    println("[ROOM_SCHEDULE] -> INIT")
  }

  private fun enterActive() {
    state = State.ACTIVE
    cancelTimer()
    emitZoneActive()
    println("[ROOM_SCHEDULE] -> ACTIVE")
  }

  private fun enterInactive() {
    state = State.INACTIVE
    cancelTimer()
    emitZoneInactive()
    println("[ROOM_SCHEDULE] -> INACTIVE")
  }

  private fun enterWarning() {
    state = State.WARNING
    cancelTimer()
    emitZoneWarning()
    // CSML: after 300000ms (5min) -> shutdownRoom -> inactive
    timer = scheduler.schedule({ onShutdownRoom() }, 300000, TimeUnit.MILLISECONDS)
    println("[ROOM_SCHEDULE] -> WARNING (5min grace)")
  }

  private fun enterAuthorizedStay() {
    state = State.AUTHORIZED_STAY
    cancelTimer()
    emitZoneActive()
    // CSML: after 7200000ms (2hr) -> recheckOverride -> warning
    timer = scheduler.schedule({ onRecheckOverride() }, 7200000, TimeUnit.MILLISECONDS)
    println("[ROOM_SCHEDULE] -> AUTHORIZED_STAY (2hr lease)")
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelTimer()
    println("[ROOM_SCHEDULE] -> EMERGENCY")
  }

  // Internal timeout handlers
  private fun onShutdownRoom() {
    if (state == State.WARNING) enterInactive()
  }

  private fun onRecheckOverride() {
    if (state == State.AUTHORIZED_STAY) enterWarning()
  }

  // External events
  override fun onEnterBusinessHours() {
    when (state) {
      State.INACTIVE, State.WARNING, State.AUTHORIZED_STAY -> enterActive()
      else -> {}
    }
  }

  override fun onEnterAfterHours() {
    when (state) {
      State.ACTIVE -> enterWarning()
      else -> {}
    }
  }

  override fun onEnterWeekend() {
    when (state) {
      State.ACTIVE -> enterWarning()
      else -> {}
    }
  }

  override fun onRequestStay() {
    when (state) {
      State.INACTIVE, State.WARNING -> enterAuthorizedStay()
      else -> {}
    }
  }

  override fun onUserLeftZone() {
    when (state) {
      State.AUTHORIZED_STAY -> enterInactive()
      else -> {}
    }
  }

  override fun onFireAlarm() {
    fireAlarm = true
    when (state) {
      State.ACTIVE, State.INACTIVE, State.WARNING, State.AUTHORIZED_STAY -> enterEmergency()
      else -> {}
    }
  }

  override fun onGasLeakDetected() {
    gasActive = true
    when (state) {
      State.ACTIVE, State.INACTIVE, State.WARNING, State.AUTHORIZED_STAY -> enterEmergency()
      else -> {}
    }
  }

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterInactive()
  }

  override fun onGasPurged() {
    gasActive = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterInactive()
  }
}
