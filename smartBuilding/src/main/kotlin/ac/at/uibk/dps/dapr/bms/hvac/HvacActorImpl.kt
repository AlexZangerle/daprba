package ac.at.uibk.dps.dapr.bms.hvac

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.time.Duration

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

  override fun initialize() {
    enterOff()
  }

  private fun schedulePoll(delayMs: Long) {
    cancelPoll()
    super.registerActorTimer(
        "tempPollTimer",
        "onTempPollTimer",
        Duration.ofMillis(delayMs),
        Duration.ofMillis(-1),
      )
      .block()
  }

  private fun cancelPoll() {
    super.unregisterActorTimer("tempPollTimer").block()
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
    pollTemp()
    schedulePoll(300000)
  }

  private fun enterHeating() {
    state = State.HEATING
    service.setHvac("heat", roomId)
    pollTemp()
    schedulePoll(600000)
  }

  private fun enterCooling() {
    state = State.COOLING
    service.setHvac("cool", roomId)
    pollTemp()
    schedulePoll(600000)
  }

  private fun enterEnergySaving() {
    state = State.ENERGY_SAVING
    service.setHvac("fan", roomId)
    tempTolerance = energySavingTolerance
    pollTemp()
    schedulePoll(900000)
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelPoll()
    service.setHvac("off", roomId)
    drasticSaving = false
    tempTolerance = defaultTolerance
  }

  override fun onOccupancyDetected() {
    if (state == State.OFF) enterFanOnly()
  }

  override fun onOccupancyVacant() {
    if (
      state == State.FAN_ONLY ||
        state == State.HEATING ||
        state == State.COOLING ||
        state == State.ENERGY_SAVING
    )
      enterOff()
  }

  override fun onZoneActive() {
    if (state == State.OFF) enterFanOnly()
  }

  override fun onZoneInactive() {
    if (
      state == State.FAN_ONLY ||
        state == State.HEATING ||
        state == State.COOLING ||
        state == State.ENERGY_SAVING
    )
      enterOff()
  }

  override fun onEnterBusinessHours() {
    desiredTemp = businessHoursTemp
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING) pollTemp()
  }

  override fun onEnterAfterHours() {
    desiredTemp = afterHoursTemp
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING) pollTemp()
  }

  override fun onEnterWeekend() {
    desiredTemp = afterHoursTemp
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING) pollTemp()
  }

  override fun onActivateEnergySaving() {
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING)
      enterEnergySaving()
  }

  override fun onDrasticEnergySaving() {
    if (state == State.FAN_ONLY || state == State.HEATING || state == State.COOLING)
      enterEnergySaving()
    else if (state == State.ENERGY_SAVING) drasticSaving = true
  }

  override fun onDeactivateEnergySaving() {
    if (state == State.ENERGY_SAVING) enterFanOnly()
  }

  override fun onEnergyShutdown() {
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onFireAlarm() {
    isFireAlarm = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onGasLeakDetected() {
    isGasActive = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onSmokeAlert() {
    isSmokeAlert = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onArcFaultDetected() {
    arcFault = true
    if (state != State.EMERGENCY) enterEmergency()
  }

  override fun onDisarmFireAlarm() {
    isFireAlarm = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }

  override fun onGasPurged() {
    isGasActive = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }

  override fun onDisarmSmokeAlert() {
    isSmokeAlert = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }

  override fun onResetElectricalFault() {
    arcFault = false
    if (state == State.EMERGENCY && !isFireAlarm && !isGasActive && !isSmokeAlert && !arcFault)
      enterOff()
  }
}
