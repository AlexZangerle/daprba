package ac.at.uibk.dps.dapr.bms.benchmark

import io.javalin.Javalin
import io.javalin.http.Context
import java.io.File
import java.io.Serializable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import kotlin.time.Clock
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

// === Binding classes (must match daprba bindings) ===
data class FireDetectionRequest(val imageData: String, val zoneId: String) : Serializable
data class FireDetectionResponse(val fireDetectionResult: String, val emergencyInRoom: String) : Serializable
data class FireDoorRequest(val doorId: String) : Serializable
data class HvacRequest(val mode: String, val roomId: String) : Serializable
data class IndoorTempResponse(val indoorTemp: Double) : Serializable
data class RoomRequest(val roomId: String) : Serializable
data class RoomTempResponse(val roomTemp: Double) : Serializable
data class OccupancyRequest(val imageData: String) : Serializable
data class OccupancyResponse(val occupancyDetected: Boolean) : Serializable
data class OutdoorTempResponse(val outdoorTemp: Double) : Serializable
data class EnergyPriceResponse(val energyPrice: Double) : Serializable
data class GridStatusResponse(val gridStatus: String) : Serializable
data class ScheduleModeResponse(val currentSchedule: String) : Serializable
data class GasLeakResponse(val gasLeakLocation: String) : Serializable
data class GasValveRequest(val gasLeakLocation: String) : Serializable
data class ArcFaultResponse(val arcFaultLocation: String) : Serializable
data class TripCircuitBreakerRequest(val arcFaultLocation: String) : Serializable
data class AuthRequest(val cardId: String) : Serializable
data class AuthResponse(val userId: String, val userRole: String, val authenticationStatus: String) : Serializable
data class AccessRuleRequest(val userId: String, val userRole: String, val doorId: String, val currentScheduleMode: String) : Serializable
data class AccessDecisionResponse(val accessDecision: String) : Serializable
data class DoorLockRequest(val doorId: String, val command: String) : Serializable
data class DoorRouteResponse(val isEvacuationRoute: Boolean, val zoneId: String) : Serializable
data class SecurityNotificationRequest(val message: String) : Serializable
data class EvacuationLightsRequest(val roomId: String, val emergencyRoomId: String) : Serializable
data class LightLevelRequest(val roomId: String, val lightLevel: Int) : Serializable
data class BlindLevelRequest(val roomId: String, val blindLevel: Int) : Serializable
data class EmptyRequest(val dummy: String = "") : Serializable

// === Fury XLANG serializer ===
object FuryXlang {
    val fory: ThreadSafeFory = Fory.builder()
        .withLanguage(Language.XLANG)
        .withRefTracking(true)
        .buildThreadSafeFory()
        .apply {
            listOf(
                FireDetectionRequest::class.java, FireDetectionResponse::class.java,
                FireDoorRequest::class.java, HvacRequest::class.java,
                IndoorTempResponse::class.java, RoomRequest::class.java,
                RoomTempResponse::class.java, OccupancyRequest::class.java,
                OccupancyResponse::class.java, OutdoorTempResponse::class.java,
                EnergyPriceResponse::class.java, GridStatusResponse::class.java,
                ScheduleModeResponse::class.java, GasLeakResponse::class.java,
                GasValveRequest::class.java, ArcFaultResponse::class.java,
                TripCircuitBreakerRequest::class.java, AuthRequest::class.java,
                AuthResponse::class.java, AccessRuleRequest::class.java,
                AccessDecisionResponse::class.java, DoorLockRequest::class.java,
                DoorRouteResponse::class.java, SecurityNotificationRequest::class.java,
                EvacuationLightsRequest::class.java, LightLevelRequest::class.java,
                BlindLevelRequest::class.java, EmptyRequest::class.java,
            ).forEach { register(it, it.simpleName) }
        }

    private val buf = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }

    fun serialize(obj: Any): ByteArray {
        val b = buf.get().apply { writerIndex(0) }
        fory.serialize(b, obj)
        return b.getBytes(0, b.writerIndex())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> deserialize(bytes: ByteArray): T = fory.deserialize(bytes) as T
}

// === Logging ===
data class BenchmarkEntry(val epochNs: Long, val eventType: String, val detail: String)

fun log(msg: String) {
    val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    println("  [$time] $msg")
}

fun epochNanos(): Long {
    val now = Clock.System.now()
    return (now.epochSeconds * 1_000_000_000L) + now.nanosecondsOfSecond
}

object BenchmarkLog {
    private val csvFile = File("/metrics/building_service.csv")
    init {
        csvFile.parentFile.mkdirs()
        csvFile.writeText("epoch_ns,event_type,detail\n")
    }
    fun logEvent(eventType: String, detail: String) {
        csvFile.appendText("${epochNanos()},$eventType,$detail\n")
    }
}

fun respondWith(ctx: Context, obj: Any) {
    ctx.contentType("application/octet-stream")
    ctx.result(FuryXlang.serialize(obj))
}

fun respondEmpty(ctx: Context) {
    ctx.status(200)
    ctx.result("")
}

inline fun <reified T> parseInput(ctx: Context): T = FuryXlang.deserialize(ctx.bodyAsBytes())

fun main() {
    println("  Building Service — Full BMS (S6 Dapr)")

    val gasFaultProb = 0.05
    val arcFaultProb = 0.05
    val fireFaultProb = 0.05
    val highTempProb = 0.02

    val app = Javalin.create().start(8005)

    // === Gas Safety ===
    app.post("/checkGasFault") { ctx ->
        val location = if (ThreadLocalRandom.current().nextDouble() < gasFaultProb) {
            log("[GAS] FAULT detected in Zone-A")
            BenchmarkLog.logEvent("gas_fault", "Zone-A")
            "Zone-A"
        } else {
            log("[GAS] poll — no fault")
            "none"
        }
        BenchmarkLog.logEvent("gas_poll", "checkGasFault")
        respondWith(ctx, GasLeakResponse(location))
    }

    app.post("/closeGasValve") { ctx ->
        val req: GasValveRequest = parseInput(ctx)
        log("[GAS] closeGasValve location=${req.gasLeakLocation}")
        BenchmarkLog.logEvent("gas_action", "closeGasValve")
        respondEmpty(ctx)
    }

    app.post("/cutPower") { ctx ->
        val req: GasValveRequest = parseInput(ctx)
        log("[GAS] cutPower location=${req.gasLeakLocation}")
        BenchmarkLog.logEvent("gas_action", "cutPower")
        respondEmpty(ctx)
    }

    app.post("/gasLeakPurged") { ctx ->
        log("[GAS] gasLeakPurged — reset complete")
        BenchmarkLog.logEvent("gas_reset", "gasLeakPurged")
        respondEmpty(ctx)
    }

    // === Electrical Safety ===
    app.post("/checkArcFault") { ctx ->
        val location = if (ThreadLocalRandom.current().nextDouble() < arcFaultProb) {
            log("[ELEC] FAULT detected at Panel-B")
            BenchmarkLog.logEvent("elec_fault", "Panel-B")
            "Panel-B"
        } else {
            log("[ELEC] poll — no fault")
            "none"
        }
        BenchmarkLog.logEvent("elec_poll", "checkArcFault")
        respondWith(ctx, ArcFaultResponse(location))
    }

    app.post("/tripCircuitBreaker") { ctx ->
        val req: TripCircuitBreakerRequest = parseInput(ctx)
        log("[ELEC] tripCircuitBreaker location=${req.arcFaultLocation}")
        BenchmarkLog.logEvent("elec_action", "tripCircuitBreaker")
        respondEmpty(ctx)
    }

    app.post("/ackElectrical") { ctx ->
        log("[ELEC] ackElectrical — reset complete")
        BenchmarkLog.logEvent("elec_reset", "ackElectrical")
        respondEmpty(ctx)
    }

    // === Fire Detection ===
    app.post("/detectFire") { ctx ->
        val req: FireDetectionRequest = parseInput(ctx)
        val result = if (ThreadLocalRandom.current().nextDouble() < fireFaultProb) {
            log("[FIRE] DETECTED in ${req.zoneId}")
            BenchmarkLog.logEvent("fire_detected", req.zoneId)
            "fire"
        } else {
            log("[FIRE] poll (${req.zoneId}) — no fire")
            "none"
        }
        respondWith(ctx, FireDetectionResponse(result, req.zoneId))
    }

    app.post("/closeFireDoor") { ctx ->
        val req: FireDoorRequest = parseInput(ctx)
        log("[FIRE] closeFireDoor ${req.doorId}")
        BenchmarkLog.logEvent("door_close", req.doorId)
        respondEmpty(ctx)
    }

    app.post("/openFireDoor") { ctx ->
        val req: FireDoorRequest = parseInput(ctx)
        log("[FIRE] openFireDoor ${req.doorId}")
        BenchmarkLog.logEvent("door_open", req.doorId)
        respondEmpty(ctx)
    }

    // === HVAC ===
    app.post("/setHVAC") { ctx ->
        val req: HvacRequest = parseInput(ctx)
        log("[HVAC] setHVAC mode=${req.mode} room=${req.roomId}")
        BenchmarkLog.logEvent("hvac", "${req.mode} (${req.roomId})")
        respondEmpty(ctx)
    }

    app.post("/getIndoorTemp") { ctx ->
        log("[HVAC] getIndoorTemp → 21.0")
        respondWith(ctx, IndoorTempResponse(21.0))
    }

    // === Temperature Safety ===
    app.post("/getRoomTemp") { ctx ->
        val temp = if (ThreadLocalRandom.current().nextDouble() < highTempProb) {
            log("[TEMP] HIGH RISK — returning 65.0")
            BenchmarkLog.logEvent("temp_high", "65.0")
            65.0
        } else {
            log("[TEMP] poll → 22.0")
            22.0
        }
        respondWith(ctx, RoomTempResponse(temp))
    }

    app.post("/highRiskTemp") { ctx ->
        log("[TEMP] highRiskTemp action")
        BenchmarkLog.logEvent("temp_action", "highRisk")
        respondEmpty(ctx)
    }

    // === Occupancy ===
    app.post("/detectOccupancy") { ctx ->
        val detected = ThreadLocalRandom.current().nextDouble() < 0.7
        log("[OCCUPANCY] detectOccupancy → $detected")
        respondWith(ctx, OccupancyResponse(detected))
    }

    app.post("/maintenance") { ctx ->
        log("[OCCUPANCY] maintenance")
        BenchmarkLog.logEvent("occupancy", "maintenance")
        respondEmpty(ctx)
    }

    // === Lighting ===
    app.post("/turnOn") { ctx ->
        log("[LIGHT] turnOn")
        BenchmarkLog.logEvent("lighting", "on")
        respondEmpty(ctx)
    }

    app.post("/turnOff") { ctx ->
        log("[LIGHT] turnOff")
        BenchmarkLog.logEvent("lighting", "off")
        respondEmpty(ctx)
    }

    app.post("/dim") { ctx ->
        log("[LIGHT] dim")
        BenchmarkLog.logEvent("lighting", "dim")
        respondEmpty(ctx)
    }

    app.post("/evacuationLights") { ctx ->
        val req: EvacuationLightsRequest = parseInput(ctx)
        log("[LIGHT] evacuationLights room=${req.roomId} emergency=${req.emergencyRoomId}")
        BenchmarkLog.logEvent("lighting", "evacuation")
        respondEmpty(ctx)
    }

    app.post("/userLevelLight") { ctx ->
        val req: LightLevelRequest = parseInput(ctx)
        log("[LIGHT] userLevelLight room=${req.roomId} level=${req.lightLevel}")
        BenchmarkLog.logEvent("lighting", "userLevel")
        respondEmpty(ctx)
    }

    // === Shading ===
    app.post("/blindsOpen") { ctx ->
        log("[SHADING] blindsOpen")
        BenchmarkLog.logEvent("shading", "open")
        respondEmpty(ctx)
    }

    app.post("/blindsHalf") { ctx ->
        log("[SHADING] blindsHalf")
        BenchmarkLog.logEvent("shading", "half")
        respondEmpty(ctx)
    }

    app.post("/blindsClose") { ctx ->
        log("[SHADING] blindsClose")
        BenchmarkLog.logEvent("shading", "close")
        respondEmpty(ctx)
    }

    app.post("/userLevelBlinds") { ctx ->
        val req: BlindLevelRequest = parseInput(ctx)
        log("[SHADING] userLevelBlinds room=${req.roomId} level=${req.blindLevel}")
        BenchmarkLog.logEvent("shading", "userLevel")
        respondEmpty(ctx)
    }

    app.post("/getOutdoorTemp") { ctx ->
        val temp = 10.0 + ThreadLocalRandom.current().nextDouble() * 15.0
        log("[SHADING] getOutdoorTemp → ${String.format("%.1f", temp)}")
        respondWith(ctx, OutdoorTempResponse(temp))
    }

    // === Energy Management ===
    app.post("/getEnergyPrice") { ctx ->
        val price = 20.0 + ThreadLocalRandom.current().nextDouble() * 40.0
        log("[ENERGY] getEnergyPrice → ${String.format("%.1f", price)}")
        respondWith(ctx, EnergyPriceResponse(price))
    }

    app.post("/checkGridStatus") { ctx ->
        val status = if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            log("[ENERGY] checkGridStatus → demandResponse")
            "demandResponse"
        } else {
            log("[ENERGY] checkGridStatus → normal")
            "normal"
        }
        respondWith(ctx, GridStatusResponse(status))
    }

    app.post("/normal") { ctx ->
        log("[ENERGY] normal mode")
        BenchmarkLog.logEvent("energy", "normal")
        respondEmpty(ctx)
    }

    app.post("/response") { ctx ->
        log("[ENERGY] response mode")
        BenchmarkLog.logEvent("energy", "response")
        respondEmpty(ctx)
    }

    app.post("/peak") { ctx ->
        log("[ENERGY] peak mode")
        BenchmarkLog.logEvent("energy", "peak")
        respondEmpty(ctx)
    }

    // === Building Schedule ===
    app.post("/getScheduleMode") { ctx ->
        log("[SCHEDULE] getScheduleMode → businessHours")
        respondWith(ctx, ScheduleModeResponse("businessHours"))
    }

    // === Security ===
    app.post("/authenticateUser") { ctx ->
        log("[SECURITY] authenticateUser → Success")
        respondWith(ctx, AuthResponse("user1", "employee", "Success"))
    }

    app.post("/checkAccessRule") { ctx ->
        val decision = if (ThreadLocalRandom.current().nextDouble() < 0.10) {
            log("[SECURITY] checkAccessRule → Deny")
            "Deny"
        } else {
            log("[SECURITY] checkAccessRule → Allow")
            "Allow"
        }
        respondWith(ctx, AccessDecisionResponse(decision))
    }

    app.post("/controlDoorLock") { ctx ->
        val req: DoorLockRequest = parseInput(ctx)
        log("[SECURITY] controlDoorLock door=${req.doorId} command=${req.command}")
        BenchmarkLog.logEvent("security", "doorLock:${req.command}")
        respondEmpty(ctx)
    }

    app.post("/notifySecurity") { ctx ->
        val req: SecurityNotificationRequest = parseInput(ctx)
        log("[SECURITY] notifySecurityPersonnel: ${req.message}")
        BenchmarkLog.logEvent("security", "notify")
        respondEmpty(ctx)
    }

    app.post("/initializeZone") { ctx ->
        log("[SECURITY] initializeZone")
        BenchmarkLog.logEvent("security", "initZone")
        respondEmpty(ctx)
    }

    app.post("/getDoorRouteType") { ctx ->
        log("[SECURITY] getDoorRouteType → evacuation=false, zone=zone1")
        respondWith(ctx, DoorRouteResponse(false, "zone1"))
    }

    println("  Running on port 8005")
    println("  Fault probabilities: gas=${(gasFaultProb*100).toInt()}%, arc=${(arcFaultProb*100).toInt()}%, fire=${(fireFaultProb*100).toInt()}%, temp=${(highTempProb*100).toInt()}%")
}
