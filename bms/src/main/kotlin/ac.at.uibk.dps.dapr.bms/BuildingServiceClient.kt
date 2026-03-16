package ac.at.uibk.dps.dapr.bms

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** HTTP client for the building service (Flask). */
class BuildingServiceClient(private val baseUrl: String = "http://localhost:8005") {

  private val client = HttpClient.newHttpClient()

  private fun post(endpoint: String, body: String = "") {
    try {
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl$endpoint"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept {
        println("  [SERVICE] $endpoint -> ${it.statusCode()}")
      }
    } catch (e: Exception) { println("  [SERVICE ERROR] $endpoint: ${e.message}") }
  }

  private fun postAsync(endpoint: String, body: String, handler: (String) -> Unit) {
    try {
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl$endpoint"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept {
        handler(it.body())
      }
    } catch (e: Exception) { println("  [SERVICE ERROR] $endpoint: ${e.message}") }
  }

  // Lighting
  fun turnOn(roomId: String) = post("/turnOn", """{"roomId":"$roomId"}""")
  fun turnOff(roomId: String) = post("/turnOff", """{"roomId":"$roomId"}""")
  fun dim(roomId: String) = post("/dim", """{"roomId":"$roomId"}""")
  fun turnUserLevel(roomId: String, lightLevel: Int) =
    post("/userLevelLight", """{"roomId":"$roomId","lightLevel":$lightLevel}""")
  fun evacuationLights(roomId: String, emergencyRoomId: String) =
    post("/evacuationLights", """{"roomId":"$roomId","emergencyRoomId":"$emergencyRoomId"}""")

  // HVAC
  fun setHvac(mode: String, roomId: String) =
    post("/setHVAC", """{"mode":"$mode","roomId":"$roomId"}""")

  fun getIndoorTemp(roomId: String, callback: (Double) -> Unit) {
    postAsync("/getIndoorTemp", """{"roomId":"$roomId"}""") { body ->
      Regex(""""indoorTemp"\s*:\s*([0-9.]+)""").find(body)?.let { callback(it.groupValues[1].toDouble()) }
    }
  }

  // Shading
  fun blindsHalf(roomId: String) = post("/blindsHalf", """{"roomId":"$roomId"}""")
  fun blindsOpen(roomId: String) = post("/blindsOpen", """{"roomId":"$roomId"}""")
  fun blindsClose(roomId: String) = post("/blindsClose", """{"roomId":"$roomId"}""")
  fun blindsUserLevel(roomId: String, blindLevel: Int) =
    post("/userLevelBlinds", """{"roomId":"$roomId","blindLevel":$blindLevel}""")

  fun getOutdoorTemp(callback: (Double) -> Unit) {
    postAsync("/getOutdoorTemp", "{}") { body ->
      Regex(""""outdoorTemp"\s*:\s*([0-9.]+)""").find(body)?.let { callback(it.groupValues[1].toDouble()) }
    }
  }

  // Room Occupancy
  fun detectOccupancy(imageData: String, callback: (Boolean) -> Unit) {
    postAsync("/detectOccupancy", """{"imageData":"$imageData"}""") { body ->
      val match = Regex(""""occupancyDetected"\s*:\s*(true|false)""").find(body)
      callback(match?.groupValues?.get(1)?.toBoolean() ?: false)
    }
  }

  fun maintenance(roomId: String) = post("/maintenance", """{"roomId":"$roomId"}""")

  // Fire
  fun detectFire(imageData: String, zoneId: String, callback: (String, String) -> Unit) {
    postAsync("/detectFire", """{"imageData":"$imageData","zoneId":"$zoneId"}""") { body ->
      val result = Regex(""""fireDetectionResult"\s*:\s*"(\w+)"""").find(body)?.groupValues?.get(1) ?: "none"
      val room = Regex(""""emergencyInRoom"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "none"
      callback(result, room)
    }
  }

  // Fire Door
  fun openFireDoor(doorId: String) = post("/openFireDoor", """{"doorId":"$doorId"}""")
  fun closeFireDoor(doorId: String) = post("/closeFireDoor", """{"doorId":"$doorId"}""")

  // Electrical Safety
  fun checkArcFault(callback: (String) -> Unit) {
    postAsync("/checkArcFault", "{}") { body ->
      callback(Regex(""""arcFaultLocation"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "none")
    }
  }

  fun tripCircuitBreaker(location: String) =
    post("/tripCircuitBreaker", """{"arcFaultLocation":"$location"}""")

  fun acknowledgedElectrical(callback: () -> Unit) {
    postAsync("/electricalFaultAcknowledged", "{}") { callback() }
  }

  // Gas Safety
  fun checkGasLeak(callback: (String) -> Unit) {
    postAsync("/checkGasFault", "{}") { body ->
      callback(Regex(""""gasLeakLocation"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "none")
    }
  }

  fun closeGasValve(location: String) = post("/closeGasValve", """{"gasLeakLocation":"$location"}""")
  fun cutPower(location: String) = post("/cutPower", """{"gasLeakLocation":"$location"}""")
  fun gasLeakPurged() = post("/gasLeakPurged")

  // Temp Safety
  fun getRoomTemp(roomId: String, callback: (Double) -> Unit) {
    postAsync("/getRoomTemp", """{"roomId":"$roomId"}""") { body ->
      Regex(""""roomTemp"\s*:\s*([0-9.]+)""").find(body)?.let { callback(it.groupValues[1].toDouble()) }
    }
  }

  fun highRiskTemp(roomId: String) = post("/highRiskTemp", """{"roomId":"$roomId"}""")

  // Building Schedule
  fun getScheduleMode(callback: (String) -> Unit) {
    postAsync("/getScheduleMode", "{}") { body ->
      callback(Regex(""""currentSchedule"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "afterHours")
    }
  }

  // Room Schedule
  fun initializeZone(callback: () -> Unit) {
    postAsync("/initializeZone", "{}") { callback() }
  }

  // Energy Management
  fun getEnergyPrice(callback: (Double) -> Unit) {
    postAsync("/getEnergyPrice", "{}") { body ->
      Regex(""""energyPrice"\s*:\s*([0-9.]+)""").find(body)?.let { callback(it.groupValues[1].toDouble()) }
    }
  }

  fun checkGridStatus(callback: (String) -> Unit) {
    postAsync("/checkGridStatus", "{}") { body ->
      callback(Regex(""""gridStatus"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "normal")
    }
  }

  // Security Manager
  fun notifySecurity(message: String) = post("/notifySecurity", """{"message":"$message"}""")

  // Access Control
  fun getDoorRouteType(doorId: String, callback: (Boolean, String) -> Unit) {
    postAsync("/getDoorRouteType", """{"doorId":"$doorId"}""") { body ->
      val evacRoute = Regex(""""isEvacuationRoute"\s*:\s*(true|false)""").find(body)?.groupValues?.get(1)?.toBoolean() ?: false
      val zone = Regex(""""zoneId"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
      callback(evacRoute, zone)
    }
  }

  fun authenticateUser(cardId: String, callback: (String, String, String) -> Unit) {
    postAsync("/authenticateUser", """{"cardId":"$cardId"}""") { body ->
      val userId = Regex(""""userId"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
      val userRole = Regex(""""userRole"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
      val authStatus = Regex(""""authenticationStatus"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "Failed"
      callback(userId, userRole, authStatus)
    }
  }

  fun checkAccessRule(userRole: String, zoneId: String, scheduleMode: String, callback: (String) -> Unit) {
    postAsync("/checkAccessRule", """{"userRole":"$userRole","zoneId":"$zoneId","currentScheduleMode":"$scheduleMode"}""") { body ->
      callback(Regex(""""accessDecision"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "Deny")
    }
  }

  fun controlDoorLock(doorId: String, command: String) =
    post("/controlDoorLock", """{"doorId":"$doorId","command":"$command"}""")
}
