package ac.at.uibk.dps.dapr.bms

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** HTTP client for the building service */
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

  fun turnOn(roomId: String) = post("/turnOn", """{"roomId":"$roomId"}""")

  fun turnOff(roomId: String) = post("/turnOff", """{"roomId":"$roomId"}""")

  fun dim(roomId: String) = post("/dim", """{"roomId":"$roomId"}""")

  fun turnUserLevel(roomId: String, lightLevel: Int) =
    post("/userLevelLight", """{"roomId":"$roomId","lightLevel":$lightLevel}""")

  fun evacuationLights(roomId: String, emergencyRoomId: String) =
    post("/evacuationLights", """{"roomId":"$roomId","emergencyRoomId":"$emergencyRoomId"}""")
}
