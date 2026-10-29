package fr.rakambda.filesecure.metadata

import io.github.oshai.kotlinlogging.KotlinLogging
import net.iakovlev.timeshape.TimeZoneEngine
import java.time.ZoneId
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger {}

fun interface TimeZoneResolver{
	fun resolve(latitude: Double, longitude: Double): ZoneId?
}

/**
 * Resolves the time zone of coordinates offline, with the time zone boundaries bundled by timeshape.
 *
 * The data is loaded on the first call only, as it takes time and memory. If it can't be loaded, every call returns null and loading isn't retried.
 */
class OfflineTimeZoneResolver(engineFactory: () -> TimeZoneEngine = TimeZoneEngine::initialize) : TimeZoneResolver{
	private val engine: Result<TimeZoneEngine> by lazy {
		try{
			Result.success(engineFactory())
		}
		catch(e: Throwable){
			if(e !is Exception && e !is LinkageError && e !is OutOfMemoryError){
				throw e
			}
			Result.failure(e)
		}
	}
	private val cache = ConcurrentHashMap<Pair<Double, Double>, Optional<ZoneId>>()
	private val initFailureLogged = AtomicBoolean()
	private val notFoundLogged = AtomicBoolean()

	override fun resolve(latitude: Double, longitude: Double): ZoneId?{
		val engine = engine.getOrElse { e ->
			if(initFailureLogged.compareAndSet(false, true)){
				log.warn { "Failed to load the time zone data for coordinates $latitude;$longitude, the system time zone is used for every geotagged file: ${e.javaClass.simpleName}: ${e.message}" }
			}
			return null
		}
		return cache.computeIfAbsent(latitude to longitude) {
			engine.query(latitude, longitude).also { zone ->
				if(zone.isEmpty){
					if(notFoundLogged.compareAndSet(false, true)){
						log.warn { "No time zone found for coordinates $latitude;$longitude, the system time zone is used (next ones are logged at debug level)" }
					}
					else{
						log.debug { "No time zone found for coordinates $latitude;$longitude, the system time zone is used" }
					}
				}
			}
		}.getOrNull()
	}
}
