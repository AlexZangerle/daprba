package ac.at.uibk.dps.dapr.bms

import ac.at.uibk.dps.dapr.bms.bindings.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

class BuildingServiceClient(private val baseUrl: String = "http://localhost:8005") {

  private val client = HttpClient.newHttpClient()

  companion object {
    private val fory: ThreadSafeFory =
      Fory.builder()
        .withLanguage(Language.XLANG)
        .withRefTracking(true)
        .buildThreadSafeFory()
        .apply {
          listOf(
              EmptyRequest::class.java,
              RoomRequest::class.java,
              LightLevelRequest::class.java,
              EvacuationLightsRequest::class.java,
              HvacRequest::class.java,
              IndoorTempResponse::class.java,
              BlindLevelRequest::class.java,
              OutdoorTempResponse::class.java,
              OccupancyRequest::class.java,
              OccupancyResponse::class.java,
              FireDetectionRequest::class.java,
              FireDetectionResponse::class.java,
              FireDoorRequest::class.java,
              ArcFaultResponse::class.java,
              TripCircuitBreakerRequest::class.java,
              GasLeakResponse::class.java,
              GasValveRequest::class.java,
              RoomTempResponse::class.java,
              ScheduleModeResponse::class.java,
              EnergyPriceResponse::class.java,
              GridStatusResponse::class.java,
              SecurityNotificationRequest::class.java,
              DoorRouteResponse::class.java,
              AuthRequest::class.java,
              AuthResponse::class.java,
              AccessRuleRequest::class.java,
              AccessDecisionResponse::class.java,
              DoorLockRequest::class.java,
            )
            .forEach { register(it, it.simpleName) }
        }

    private val threadBuffer = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }
  }

  private fun action(path: String, req: Any = EmptyRequest()) = execute<Unit>(path, req, null, null)

  private inline fun <reified T> query(
    path: String,
    req: Any = EmptyRequest(),
    noinline cb: (T) -> Unit,
  ) = execute(path, req, T::class.java, cb)

  private fun <T> execute(path: String, req: Any, resClass: Class<T>?, cb: ((T) -> Unit)?) {
    try {
      val buffer = threadBuffer.get().apply { writerIndex(0) }
      fory.serialize(buffer, req)

      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl$path"))
          .header("Content-Type", "application/x-fury")
          .POST(HttpRequest.BodyPublishers.ofByteArray(buffer.getBytes(0, buffer.writerIndex())))
          .build()

      client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept { res ->
        if (res.statusCode() in 200..299) {
          if (cb != null && resClass != null) {
            @Suppress("UNCHECKED_CAST") cb(fory.deserialize(res.body()) as T)
          }
        } else {
          println("  [SERVICE ERROR] $path -> Status: ${res.statusCode()}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] $path: ${e.message}")
    }
  }

  // Lighting
  fun turnOn(roomId: String) = action("/turnOn", RoomRequest(roomId))

  fun turnOff(roomId: String) = action("/turnOff", RoomRequest(roomId))

  fun dim(roomId: String) = action("/dim", RoomRequest(roomId))

  fun turnUserLevel(roomId: String, level: Int) =
    action("/userLevelLight", LightLevelRequest(roomId, level))

  fun evacuationLights(roomId: String, emergencyId: String) =
    action("/evacuationLights", EvacuationLightsRequest(roomId, emergencyId))

  // HVAC
  fun setHvac(mode: String, roomId: String) = action("/setHVAC", HvacRequest(mode, roomId))

  fun getIndoorTemp(roomId: String, cb: (Double) -> Unit) =
    query<IndoorTempResponse>("/getIndoorTemp", RoomRequest(roomId)) { cb(it.indoorTemp) }

  // Shading
  fun blindsHalf(roomId: String) = action("/blindsHalf", RoomRequest(roomId))

  fun blindsOpen(roomId: String) = action("/blindsOpen", RoomRequest(roomId))

  fun blindsClose(roomId: String) = action("/blindsClose", RoomRequest(roomId))

  fun blindsUserLevel(roomId: String, level: Int) =
    action("/userLevelBlinds", BlindLevelRequest(roomId, level))

  fun getOutdoorTemp(cb: (Double) -> Unit) =
    query<OutdoorTempResponse>("/getOutdoorTemp") { cb(it.outdoorTemp) }

  // Occupancy
  fun detectOccupancy(imageData: String, cb: (Boolean) -> Unit) =
    query<OccupancyResponse>("/detectOccupancy", OccupancyRequest(imageData)) {
      cb(it.occupancyDetected)
    }

  fun maintenance(roomId: String) = action("/maintenance", RoomRequest(roomId))

  // Fire Safety
  fun detectFire(img: String, zone: String, cb: (String, String) -> Unit) =
    query<FireDetectionResponse>("/detectFire", FireDetectionRequest(img, zone)) {
      cb(it.fireDetectionResult, it.emergencyInRoom)
    }

  fun openFireDoor(doorId: String) = action("/openFireDoor", FireDoorRequest(doorId))

  fun closeFireDoor(doorId: String) = action("/closeFireDoor", FireDoorRequest(doorId))

  // Electrical
  fun checkArcFault(cb: (String) -> Unit) =
    query<ArcFaultResponse>("/checkArcFault") { cb(it.arcFaultLocation) }

  fun tripCircuitBreaker(loc: String) =
    action("/tripCircuitBreaker", TripCircuitBreakerRequest(loc))

  fun acknowledgedElectrical(cb: () -> Unit) = action("/electricalFaultAcknowledged")

  // Gas Safety
  fun checkGasLeak(cb: (String) -> Unit) =
    query<GasLeakResponse>("/checkGasFault") { cb(it.gasLeakLocation) }

  fun closeGasValve(loc: String) = action("/closeGasValve", GasValveRequest(loc))

  fun cutPower(loc: String) = action("/cutPower", GasValveRequest(loc))

  fun gasLeakPurged() = action("/gasLeakPurged")

  // Temp Safety
  fun getRoomTemp(roomId: String, cb: (Double) -> Unit) =
    query<RoomTempResponse>("/getRoomTemp", RoomRequest(roomId)) { cb(it.roomTemp) }

  fun highRiskTemp(roomId: String) = action("/highRiskTemp", RoomRequest(roomId))

  // Schedules
  fun getScheduleMode(cb: (String) -> Unit) =
    query<ScheduleModeResponse>("/getScheduleMode") { cb(it.currentSchedule) }

  fun initializeZone(cb: () -> Unit) = action("/initializeZone")

  // Energy
  fun getEnergyPrice(cb: (Double) -> Unit) =
    query<EnergyPriceResponse>("/getEnergyPrice") { cb(it.energyPrice) }

  fun checkGridStatus(cb: (String) -> Unit) =
    query<GridStatusResponse>("/checkGridStatus") { cb(it.gridStatus) }

  // Security & Access
  fun notifySecurity(msg: String) = action("/notifySecurity", SecurityNotificationRequest(msg))

  fun getDoorRouteType(doorId: String, cb: (Boolean, String) -> Unit) =
    query<DoorRouteResponse>("/getDoorRouteType", FireDoorRequest(doorId)) {
      cb(it.isEvacuationRoute, it.zoneId)
    }

  fun authenticateUser(cardId: String, cb: (String, String, String) -> Unit) =
    query<AuthResponse>("/authenticateUser", AuthRequest(cardId)) {
      cb(it.userId, it.userRole, it.authenticationStatus)
    }

  fun checkAccessRule(role: String, zone: String, mode: String, cb: (String) -> Unit) =
    query<AccessDecisionResponse>("/checkAccessRule", AccessRuleRequest(role, zone, mode)) {
      cb(it.accessDecision)
    }

  fun controlDoorLock(doorId: String, cmd: String) =
    action("/controlDoorLock", DoorLockRequest(doorId, cmd))
}
