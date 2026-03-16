package ac.at.uibk.dps.dapr.bms.hvac

import ac.at.uibk.dps.dapr.bms.BuildingServiceClient
import io.dapr.actors.ActorId
import io.dapr.actors.runtime.AbstractActor
import io.dapr.actors.runtime.ActorRuntimeContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * HVAC actor implementation.
 *
 * States: OFF, FAN_ONLY, HEATING, COOLING, ENERGY_SAVING, EMERGENCY
 */
class HvacActorImpl(runtimeContext: ActorRuntimeContext<HvacActorImpl>, actorId: ActorId) :
  AbstractActor(runtimeContext, actorId), HvacActor {

  enum class State { OFF, FAN_ONLY, HEATING, COOLING, ENERGY_SAVING, EMERGENCY }

  private var state: State = State.OFF
  private val roomId: String = System.getenv("ROOM_ID") ?: "Room 0"

  private var indoorTemp: Double = 21.0
  private val businessHoursTemp: Double = 21.0
  private val afterHoursTemp: Double = 17.0
  private var tempTolerance: Double = 1.0
  private val defaultTolerance: Double = 1.0
  private val energySavingTolerance: Double = 3.0
  private var drasticSaving: Boolean = false
  private var desiredTemp: Double = 21.0
  private var fireAlarm: Boolean = false
  private var gasActive: Boolean = false
  private var smokeAlert: Boolean = false
  private var arcFault: Boolean = false

  private val service =
    BuildingServiceClient(System.getenv("BUILDING_SERVICE_URL") ?: "http://localhost:8005")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var tempPollTimer: ScheduledFuture<*>? = null

  init {
    enterOff()
    println("[HVAC] Initialized in OFF state, roomId=$roomId")
  }

  // Temperature polling
  private fun cancelTempPoll() {
    tempPollTimer?.cancel(false)
    tempPollTimer = null
  }

  private fun scheduleTempPoll(delayMs: Long) {
    cancelTempPoll()
    tempPollTimer = scheduler.schedule({ pollIndoorTemp() }, delayMs, TimeUnit.MILLISECONDS)
  }

  private fun pollIndoorTemp() {
    service.getIndoorTemp(roomId) { temp ->
      onTempCheckDone(temp)
    }
  }

  // Guards
  private fun isRoomTooCold(): Boolean = indoorTemp < (desiredTemp - tempTolerance)

  private fun isRoomTooHot(): Boolean = indoorTemp > (desiredTemp + tempTolerance)

  private fun isRoomComfortable(): Boolean =
    indoorTemp >= (desiredTemp - tempTolerance) && indoorTemp <= (desiredTemp + tempTolerance)

  private fun isNotDrasticSaving(): Boolean = !drasticSaving

  private fun isSafeToExitEmergency(): Boolean =
    !fireAlarm && !gasActive && !smokeAlert && !arcFault

  // State entry actions
  private fun enterOff() {
    state = State.OFF
    cancelTempPoll()
    service.setHvac("off", roomId)
    println("[HVAC] -> OFF")
  }

  private fun enterFanOnly() {
    state = State.FAN_ONLY
    service.setHvac("fan", roomId)
    tempTolerance = defaultTolerance
    pollIndoorTemp()
    scheduleTempPoll(300000)
    println("[HVAC] -> FAN_ONLY (tolerance=$tempTolerance)")
  }

  private fun enterHeating() {
    state = State.HEATING
    service.setHvac("heat", roomId)
    pollIndoorTemp()
    scheduleTempPoll(600000)
    println("[HVAC] -> HEATING")
  }

  private fun enterCooling() {
    state = State.COOLING
    service.setHvac("cool", roomId)
    pollIndoorTemp()
    scheduleTempPoll(600000)
    println("[HVAC] -> COOLING")
  }

  private fun enterEnergySaving() {
    state = State.ENERGY_SAVING
    service.setHvac("fan", roomId)
    tempTolerance = energySavingTolerance
    pollIndoorTemp()
    scheduleTempPoll(900000)
    println("[HVAC] -> ENERGY_SAVING (tolerance=$tempTolerance)")
  }

  private fun enterEmergency() {
    state = State.EMERGENCY
    cancelTempPoll()
    service.setHvac("off", roomId)
    drasticSaving = false
    tempTolerance = defaultTolerance
    println("[HVAC] -> EMERGENCY")
  }

  // Internal: temp check done
  private fun onTempCheckDone(temp: Double) {
    indoorTemp = temp
    println("[HVAC] Indoor temp: ${temp}°C (desired: $desiredTemp ± $tempTolerance)")

    when (state) {
      State.FAN_ONLY -> {
        when {
          isRoomTooCold() -> enterHeating()
          isRoomTooHot() -> enterCooling()
          else -> scheduleTempPoll(300000)
        }
      }
      State.HEATING -> {
        when {
          isRoomComfortable() -> enterFanOnly()
          isRoomTooHot() -> enterCooling()
          else -> scheduleTempPoll(600000)
        }
      }
      State.COOLING -> {
        when {
          isRoomComfortable() -> enterFanOnly()
          isRoomTooCold() -> enterHeating()
          else -> scheduleTempPoll(600000)
        }
      }
      State.ENERGY_SAVING -> {
        if (isNotDrasticSaving()) {
          when {
            isRoomTooCold() -> enterHeating()
            isRoomTooHot() -> enterCooling()
            else -> scheduleTempPoll(900000)
          }
        } else {
          scheduleTempPoll(900000)
        }
      }
      else -> {}
    }
  }

  // External event handlers
  override fun onOccupancyDetected() {
    when (state) {
      State.OFF -> enterFanOnly()
      else -> {}
    }
  }

  override fun onOccupancyVacant() {
    when (state) {
      State.FAN_ONLY, State.HEATING, State.COOLING, State.ENERGY_SAVING -> enterOff()
      else -> {}
    }
  }

  override fun onZoneActive() {
    when (state) {
      State.OFF -> enterFanOnly()
      else -> {}
    }
  }

  override fun onZoneInactive() {
    when (state) {
      State.FAN_ONLY, State.HEATING, State.COOLING, State.ENERGY_SAVING -> enterOff()
      else -> {}
    }
  }

  override fun onEnterBusinessHours() {
    desiredTemp = businessHoursTemp
    println("[HVAC] Schedule: businessHours, desiredTemp=$desiredTemp")
    when (state) {
      State.FAN_ONLY, State.HEATING, State.COOLING -> pollIndoorTemp()
      else -> {}
    }
  }

  override fun onEnterAfterHours() {
    desiredTemp = afterHoursTemp
    println("[HVAC] Schedule: afterHours, desiredTemp=$desiredTemp")
    when (state) {
      State.FAN_ONLY, State.HEATING, State.COOLING -> pollIndoorTemp()
      else -> {}
    }
  }

  override fun onEnterWeekend() {
    desiredTemp = afterHoursTemp
    println("[HVAC] Schedule: weekend, desiredTemp=$desiredTemp")
    when (state) {
      State.FAN_ONLY, State.HEATING, State.COOLING -> pollIndoorTemp()
      else -> {}
    }
  }

  override fun onActivateEnergySaving() {
    when (state) {
      State.FAN_ONLY, State.HEATING, State.COOLING -> enterEnergySaving()
      else -> {}
    }
  }

  override fun onDrasticEnergySaving() {
    when (state) {
      State.FAN_ONLY, State.HEATING, State.COOLING -> enterEnergySaving()
      State.ENERGY_SAVING -> {
        drasticSaving = true
        println("[HVAC] drasticSaving = true")
      }
      else -> {}
    }
  }

  override fun onDeactivateEnergySaving() {
    when (state) {
      State.ENERGY_SAVING -> enterFanOnly()
      else -> {}
    }
  }

  override fun onEnergyShutdown() {
    when (state) {
      State.OFF, State.FAN_ONLY, State.HEATING, State.COOLING, State.ENERGY_SAVING ->
        enterEmergency()
      else -> {}
    }
  }

  override fun onFireAlarm() {
    fireAlarm = true
    when (state) {
      State.OFF, State.FAN_ONLY, State.HEATING, State.COOLING, State.ENERGY_SAVING ->
        enterEmergency()
      else -> {}
    }
  }

  override fun onGasLeakDetected() {
    gasActive = true
    when (state) {
      State.OFF, State.FAN_ONLY, State.HEATING, State.COOLING, State.ENERGY_SAVING ->
        enterEmergency()
      else -> {}
    }
  }

  override fun onSmokeAlert() {
    smokeAlert = true
    when (state) {
      State.OFF, State.FAN_ONLY, State.HEATING, State.COOLING, State.ENERGY_SAVING ->
        enterEmergency()
      else -> {}
    }
  }

  override fun onArcFaultDetected() {
    arcFault = true
    when (state) {
      State.OFF, State.FAN_ONLY, State.HEATING, State.COOLING, State.ENERGY_SAVING ->
        enterEmergency()
      else -> {}
    }
  }

  // Safety recovery

  override fun onDisarmFireAlarm() {
    fireAlarm = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterOff()
  }

  override fun onGasPurged() {
    gasActive = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterOff()
  }

  override fun onDisarmSmokeAlert() {
    smokeAlert = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterOff()
  }

  override fun onResetElectricalFault() {
    arcFault = false
    if (state == State.EMERGENCY && isSafeToExitEmergency()) enterOff()
  }
}
