package ac.at.uibk.dps.dapr.bms.buildingSchedule

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import io.dapr.client.DaprClientBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

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

  private val metricsRegistry = BMS.metricsRegistry

  override fun initialize() {
    state = State.INIT
    service.initializeZone()
    enterInactive()
  }

  private fun cancelTimer() {
    super.unregisterTimer("roomTimer").subscribe()
  }

  private fun scheduleTimer(delayMs: Long) {
    cancelTimer()
    super.registerActorTimer(
        "roomTimer",
        "onRoomTimer",
      ByteArray(0),
        Duration.ofMillis(delayMs),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  override fun onRoomTimer() {
    when (state) {
      State.WARNING -> enterInactive()
      State.AUTHORIZED_STAY -> enterWarning()
      else -> {}
    }
  }

  private fun enterActive() {
    state = State.ACTIVE
    cancelTimer()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "zoneActive", mapOf("time" to epochNanos)).subscribe()
  }

  private fun enterInactive() {
    state = State.INACTIVE
    cancelTimer()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "zoneInactive", mapOf("time" to epochNanos)).subscribe()
  }

  private fun enterWarning() {
    state = State.WARNING
    cancelTimer()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "zoneWarning", mapOf("time" to epochNanos)).subscribe()
    scheduleTimer(300000)
  }

  private fun enterAuthorizedStay() {
    state = State.AUTHORIZED_STAY
    cancelTimer()
    val now = Clock.System.now()
    val epochNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    daprClient.publishEvent("pubsub", "zoneActive", mapOf("time" to epochNanos)).subscribe()
    scheduleTimer(7200000)
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelTimer()
  }

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun onEnterBusinessHours(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.INACTIVE || state == State.WARNING || state == State.AUTHORIZED_STAY)
      enterActive()
  }

  override fun onEnterAfterHours(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.ACTIVE) enterWarning()
  }

  override fun onEnterWeekend(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.ACTIVE) enterWarning()
  }

  override fun onRequestStay(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.INACTIVE || state == State.WARNING) enterAuthorizedStay()
  }

  override fun onUserLeftZone(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.AUTHORIZED_STAY) enterInactive()
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    fireAlarm = true
    if (
      state == State.ACTIVE ||
        state == State.INACTIVE ||
        state == State.WARNING ||
        state == State.AUTHORIZED_STAY
    )
      enterEmergency()
  }

  override fun onGasLeakDetected(data: Map<String, Any>) {
    measureEventTime(data)
    gasActive = true
    if (
      state == State.ACTIVE ||
        state == State.INACTIVE ||
        state == State.WARNING ||
        state == State.AUTHORIZED_STAY
    )
      enterEmergency()
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    fireAlarm = false
    if (state == State.EMERGENCY && !fireAlarm && !gasActive) enterInactive()
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    gasActive = false
    if (state == State.EMERGENCY && !fireAlarm && !gasActive) enterInactive()
  }
}
