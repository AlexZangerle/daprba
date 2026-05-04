package ac.at.uibk.dps.dapr.bms.bindings

import java.io.Serializable

data class RoomRequest(val roomId: String) : Serializable

data class EmptyRequest(val dummy: String = "") : Serializable

// Lighting
data class LightLevelRequest(val roomId: String, val lightLevel: Int) : Serializable

data class EvacuationLightsRequest(val roomId: String, val emergencyRoomId: String) : Serializable

// HVAC
data class HvacRequest(val mode: String, val roomId: String) : Serializable

data class IndoorTempResponse(val indoorTemp: Double) : Serializable

// Shading
data class BlindLevelRequest(val roomId: String, val blindLevel: Int) : Serializable

data class OutdoorTempResponse(val outdoorTemp: Double) : Serializable

// Occupancy-
data class OccupancyRequest(val imageData: String) : Serializable

data class OccupancyResponse(val occupancyDetected: Boolean) : Serializable

// Fire Safety
data class FireDetectionRequest(val imageData: String, val zoneId: String) : Serializable

data class FireDetectionResponse(val fireDetectionResult: String, val emergencyInRoom: String) :
  Serializable

data class FireDoorRequest(val doorId: String) : Serializable

// Electrial Safety
data class ArcFaultResponse(val arcFaultLocation: String) : Serializable

data class TripCircuitBreakerRequest(val arcFaultLocation: String) : Serializable

// Gas Safety
data class GasLeakResponse(val gasLeakLocation: String) : Serializable

data class GasValveRequest(val gasLeakLocation: String) : Serializable

// Temp Safety-
data class RoomTempResponse(val roomTemp: Double) : Serializable

// Scheduling-
data class ScheduleModeResponse(val currentSchedule: String) : Serializable

// Energy Management
data class EnergyPriceResponse(val energyPrice: Double) : Serializable

data class GridStatusResponse(val gridStatus: String) : Serializable

// Security
data class SecurityNotificationRequest(val message: String) : Serializable

// Access Control
data class DoorRouteResponse(val isEvacuationRoute: Boolean, val zoneId: String) : Serializable

data class AuthRequest(val cardId: String) : Serializable

data class AuthResponse(
  val userId: String,
  val userRole: String,
  val authenticationStatus: String,
) : Serializable

data class AccessRuleRequest(
  val userRole: String,
  val zoneId: String,
  val currentScheduleMode: String,
) : Serializable

data class AccessDecisionResponse(val accessDecision: String) : Serializable

data class DoorLockRequest(val doorId: String, val command: String) : Serializable
