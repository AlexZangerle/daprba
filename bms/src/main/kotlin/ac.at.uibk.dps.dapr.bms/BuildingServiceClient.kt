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
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl$endpoint"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept {
        println("  [SERVICE] $endpoint -> ${it.statusCode()}")
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] $endpoint: ${e.message}")
    }
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
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/getIndoorTemp"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""{"roomId":"$roomId"}"""))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val match = Regex(""""indoorTemp"\s*:\s*([0-9.]+)""").find(response.body())
          if (match != null) callback(match.groupValues[1].toDouble())
        } catch (e: Exception) {
          println("  [SERVICE ERROR] getIndoorTemp parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] getIndoorTemp: ${e.message}")
    }
  }

  // Shading
  fun blindsHalf(roomId: String) = post("/blindsHalf", """{"roomId":"$roomId"}""")
  fun blindsOpen(roomId: String) = post("/blindsOpen", """{"roomId":"$roomId"}""")
  fun blindsClose(roomId: String) = post("/blindsClose", """{"roomId":"$roomId"}""")
  fun blindsUserLevel(roomId: String, blindLevel: Int) =
    post("/userLevelBlinds", """{"roomId":"$roomId","blindLevel":$blindLevel}""")

  fun getOutdoorTemp(callback: (Double) -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/getOutdoorTemp"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val match = Regex(""""outdoorTemp"\s*:\s*([0-9.]+)""").find(response.body())
          if (match != null) callback(match.groupValues[1].toDouble())
        } catch (e: Exception) {
          println("  [SERVICE ERROR] getOutdoorTemp parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] getOutdoorTemp: ${e.message}")
    }
  }

  // Room Occupancy
  fun detectOccupancy(imageData: String, callback: (Boolean) -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/detectOccupancy"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""{"imageData":"$imageData"}"""))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val match = Regex(""""occupancyDetected"\s*:\s*(true|false)""").find(response.body())
          val detected = match?.groupValues?.get(1)?.toBoolean() ?: false
          callback(detected)
        } catch (e: Exception) {
          println("  [SERVICE ERROR] detectOccupancy parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] detectOccupancy: ${e.message}")
    }
  }

  fun maintenance(roomId: String) = post("/maintenance", """{"roomId":"$roomId"}""")

  // Fire
  fun detectFire(imageData: String, zoneId: String, callback: (String, String) -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/detectFire"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""{"imageData":"$imageData","zoneId":"$zoneId"}"""))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val body = response.body()
          val resultMatch = Regex(""""fireDetectionResult"\s*:\s*"(\w+)"""").find(body)
          val roomMatch = Regex(""""emergencyInRoom"\s*:\s*"([^"]+)"""").find(body)
          val result = resultMatch?.groupValues?.get(1) ?: "none"
          val room = roomMatch?.groupValues?.get(1) ?: "none"
          callback(result, room)
        } catch (e: Exception) {
          println("  [SERVICE ERROR] detectFire parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] detectFire: ${e.message}")
    }
  }

  // Fire Door
  fun openFireDoor(doorId: String) = post("/openFireDoor", """{"doorId":"$doorId"}""")
  fun closeFireDoor(doorId: String) = post("/closeFireDoor", """{"doorId":"$doorId"}""")

  // Electrical Safety
  fun checkArcFault(callback: (String) -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/checkArcFault"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val match = Regex(""""arcFaultLocation"\s*:\s*"([^"]+)"""").find(response.body())
          callback(match?.groupValues?.get(1) ?: "none")
        } catch (e: Exception) {
          println("  [SERVICE ERROR] checkArcFault parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] checkArcFault: ${e.message}")
    }
  }

  fun tripCircuitBreaker(location: String) =
    post("/tripCircuitBreaker", """{"arcFaultLocation":"$location"}""")

  fun acknowledgedElectrical(callback: () -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/electricalFaultAcknowledged"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { callback() }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] electricalFaultAcknowledged: ${e.message}")
    }
  }

  // Gas Safety
  fun checkGasLeak(callback: (String) -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/checkGasFault"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val match = Regex(""""gasLeakLocation"\s*:\s*"([^"]+)"""").find(response.body())
          callback(match?.groupValues?.get(1) ?: "none")
        } catch (e: Exception) {
          println("  [SERVICE ERROR] checkGasFault parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] checkGasFault: ${e.message}")
    }
  }

  fun closeGasValve(location: String) =
    post("/closeGasValve", """{"gasLeakLocation":"$location"}""")

  fun cutPower(location: String) =
    post("/cutPower", """{"gasLeakLocation":"$location"}""")

  fun gasLeakPurged() = post("/gasLeakPurged")

  // Temp Safety
  fun getRoomTemp(roomId: String, callback: (Double) -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/getRoomTemp"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""{"roomId":"$roomId"}"""))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val match = Regex(""""roomTemp"\s*:\s*([0-9.]+)""").find(response.body())
          if (match != null) callback(match.groupValues[1].toDouble())
        } catch (e: Exception) {
          println("  [SERVICE ERROR] getRoomTemp parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] getRoomTemp: ${e.message}")
    }
  }

  fun highRiskTemp(roomId: String) =
    post("/highRiskTemp", """{"roomId":"$roomId"}""")

  // Building Schedule
  fun getScheduleMode(callback: (String) -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/getScheduleMode"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { response ->
        try {
          val match = Regex(""""currentSchedule"\s*:\s*"([^"]+)"""").find(response.body())
          callback(match?.groupValues?.get(1) ?: "afterHours")
        } catch (e: Exception) {
          println("  [SERVICE ERROR] getScheduleMode parse: ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] getScheduleMode: ${e.message}")
    }
  }

  // Room Schedule
  fun initializeZone(callback: () -> Unit) {
    try {
      val request =
        HttpRequest.newBuilder()
          .uri(URI.create("$baseUrl/initializeZone"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build()
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { callback() }
    } catch (e: Exception) {
      println("  [SERVICE ERROR] initializeZone: ${e.message}")
    }
  }
}
