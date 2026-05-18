package ac.at.uibk.dps.dapr.bms.hvac

import ac.at.uibk.dps.dapr.bms.BMS
import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class HvacActorImpl(runtimeContext: ActorRuntimeContext<HvacActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), HvacActor {

  enum class State {
    OFF,
    FAN_ONLY,
    HEATING,
    COOLING,
    ENERGY_SAVING,
    EMERGENCY,
  }

  private var state: State = State.OFF
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"
  private var indoorTemp: Double = 21.0
  private var desiredTemp: Double = 21.0
  private val businessHoursTemp: Double = 21.0
  private val afterHoursTemp: Double = 17.0
  private var tempTolerance: Double = 1.0
  private val defaultTolerance: Double = 1.0
  private val energySavingTolerance: Double = 3.0
  private var drasticSaving: Boolean = false
  private var isFireAlarm: Boolean = false
  private var isGasActive: Boolean = false
  private var isSmokeAlert: Boolean = false
  private var arcFault: Boolean = false
  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val metricsRegistry = BMS.metricsRegistry

  private fun measureEventTime(data: Map<String, Any>) {
    val now = Clock.System.now()
    val nowNanos = (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
    val deltaNanos = (nowNanos - data["time"] as Long).coerceAtLeast(0L)
    metricsRegistry.timer("event.latency").update(deltaNanos, TimeUnit.NANOSECONDS)
  }

  override fun initialize() {
    enterOff()
  }

  private fun schedulePoll(delayMs: Long) {
    cancelPoll()
    super.registerActorTimer(
        "tempPollTimer",
        "onTempPollTimer",
        ByteArray(0),
        Duration.ofMillis(delayMs),
        Duration.ofMillis(-1),
      )
      .subscribe()
  }

  private fun cancelPoll() {
    super.unregisterTimer("tempPollTimer").subscribe()
  }

  override fun onTempPollTimer() {
    pollTemp()
  }

  private fun pollTemp() {
    service.getIndoorTemp(roomId) { temp ->
      indoorTemp = temp
      when (state) {
        State.FAN_ONLY ->
          when {
            indoorTemp < (desiredTemp - tempTolerance) -> enterHeating()
            indoorTemp > (desiredTemp + tempTolerance) -> enterCooling()
            else -> schedulePoll(300000)
          }
        State.HEATING ->
          when {
            indoorTemp in (desiredTemp - tempTolerance)..(desiredTemp + tempTolerance) ->
              enterFanOnly()
            indoorTemp > (desiredTemp + tempTolerance) -> enterCooling()
            else -> schedulePoll(600000)
          }
        State.COOLING ->
          when {
            indoorTemp in (desiredTemp - tempTolerance)..(desiredTemp + tempTolerance) ->
              enterFanOnly()
            indoorTemp < (desiredTemp - tempTolerance) -> enterHeating()
            else -> schedulePoll(600000)
          }
        State.ENERGY_SAVING -> {
          if (!drasticSaving)
            when {
              indoorTemp < (desiredTemp - tempTolerance) -> enterHeating()
              indoorTemp > (desiredTemp + tempTolerance) -> enterCooling()
              else -> schedulePoll(900000)
            }
          else schedulePoll(900000)
        }
        else -> {}
      }
    }
  }

  private fun enterOff() {
    state = State.OFF
    cancelPoll()
    service.setHvac("off", roomId)
  }

  private fun enterFanOnly() {
    state = State.FAN_ONLY
    service.setHvac("fan", roomId)
    tempTolerance = defaultTolerance
    schedulePoll(300000)
  }

  private fun enterHeating() {
    state = State.HEATING
    service.setHvac("heat", roomId)
    schedulePoll(600000)
  }

  private fun enterCooling() {
    state = State.COOLING
    service.setHvac("cool", roomId)
    schedulePoll(600000)
  }

  private fun enterEnergySaving() {
    state = State.ENERGY_SAVING
    service.setHvac("fan", roomId)
    tempTolerance = energySavingTolerance
    schedulePoll(900000)
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelPoll()
    service.setHvac("off", roomId)
    drasticSaving = false
    tempTolerance = defaultTolerance
  }

  override fun onOccupancyDetected(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.OFF) enterFanOnly()
  }

  override fun onOccupancyVacant(data: Map<String, Any>) {
    measureEventTime(data)
    if (
      state == State.FAN_ONLY ||
        state == State.HEATING ||
        state == State.COOLING ||
        state == State.ENERGY_SAVING
    )
      enterOff()
  }

  override fun onZoneActive(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.OFF) enterFanOnly()
  }

  override fun onZoneInactive(data: Map<String, Any>) {
    measureEventTime(data)
    if (
      state == State.FAN_ONLY ||
        state == State.HEATING ||
        state == State.COOLING ||
        state == State.ENERGY_SAVING
    )
      enterOff()
  }

  override fun onEnterBusinessHours(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.EMERGENCY) return
    desiredTemp = businessHoursTemp
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING) pollTemp()
  }

  override fun onEnterAfterHours(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.EMERGENCY) return
    desiredTemp = afterHoursTemp
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING) pollTemp()
  }

  override fun onEnterWeekend(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.EMERGENCY) return
    desiredTemp = afterHoursTemp
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING) pollTemp()
  }

  override fun onActivateEnergySaving(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING)
      enterEnergySaving()
  }

  override fun onDrasticEnergySaving(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING)
      enterEnergySaving()
    else if (state == State.ENERGY_SAVING) drasticSaving = true
  }

  override fun onDeactivateEnergySaving(data: Map<String, Any>) {
    measureEventTime(data)
    if (state == State.ENERGY_SAVING) enterFanOnly()
  }

  override fun onEnergyShutdown(data: Map<String, Any>) {
    measureEventTime(data)
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    isFireAlarm = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onGasLeakDetected(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onSmokeAlert(data: Map<String, Any>) {
    measureEventTime(data)
    isSmokeAlert = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onArcFaultDetected(data: Map<String, Any>) {
    measureEventTime(data)
    arcFault = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onDisarmFireAlarm(data: Map<String, Any>) {
    measureEventTime(data)
    isFireAlarm = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }

  override fun onGasPurged(data: Map<String, Any>) {
    measureEventTime(data)
    isGasActive = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }

  override fun onDisarmSmokeAlert(data: Map<String, Any>) {
    measureEventTime(data)
    isSmokeAlert = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }

  override fun onResetElectricalFault(data: Map<String, Any>) {
    measureEventTime(data)
    arcFault = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }
}
