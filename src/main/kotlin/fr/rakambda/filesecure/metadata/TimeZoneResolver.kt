package fr.rakambda.filesecure.metadata

import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

fun interface TimeZoneResolver{
	fun resolve(latitude: Double, longitude: Double): ZoneId?
}

/**
 * Resolves the time zone of coordinates with the geonames.org web service.
 */
class GeonamesTimeZoneResolver(private val username: String) : TimeZoneResolver{
	private val client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.build()
	private val mapper = JsonMapper()
	private val cache = ConcurrentHashMap<Pair<Double, Double>, ZoneId>()

	override fun resolve(latitude: Double, longitude: Double): ZoneId?{
		cache[latitude to longitude]?.let { return it }
		try{
			val query = "lat=$latitude&lng=$longitude&username=${URLEncoder.encode(username, StandardCharsets.UTF_8)}"
			val request = HttpRequest.newBuilder(URI.create("http://api.geonames.org/timezoneJSON?$query"))
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build()
			val response = client.send(request, HttpResponse.BodyHandlers.ofString())
			if(response.statusCode() !in 200..299){
				log.warn { "Geonames answered ${response.statusCode()} for coordinates $latitude;$longitude" }
				return null
			}
			val zoneId = mapper.readTree(response.body()).path("timezoneId").textValue()?.let(ZoneId::of)
			zoneId?.let { cache[latitude to longitude] = it }
			return zoneId
		}
		catch(e: InterruptedException){
			Thread.currentThread().interrupt()
			return null
		}
		catch(e: Exception){
			log.error(e) { "Error getting zoneID for coordinates $latitude;$longitude" }
			return null
		}
	}
}
