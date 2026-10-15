package fr.rakambda.filesecure.metadata

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.mov.QuickTimeDirectory
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory
import com.drew.metadata.mp4.Mp4Directory
import com.drew.metadata.xmp.XmpDirectory
import io.github.oshai.kotlinlogging.KotlinLogging
import us.fatehi.pointlocation6709.parse.PointLocationParser
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.TimeZone
import java.util.regex.Pattern

private val log = KotlinLogging.logger {}

class MetadataExtractor(
	private val timeZoneResolver: TimeZoneResolver,
	private val nameDateExtractors: List<NameDateExtractor> = NameDateExtractor.DEFAULTS,
){
	private val mediaDateExtractors: List<MediaDateExtractor> = listOf(
		TagMediaDateExtractor(QuickTimeMetadataDirectory::class.java, QuickTimeMetadataDirectory.TAG_CREATION_DATE),
		TagMediaDateExtractor(QuickTimeDirectory::class.java, QuickTimeDirectory.TAG_CREATION_TIME),
		TagMediaDateExtractor(Mp4Directory::class.java, Mp4Directory.TAG_CREATION_TIME),
		TagMediaDateExtractor(ExifSubIFDDirectory::class.java, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL),
		XmpMediaDateExtractor,
	)

	fun getMetadata(path: Path): FileMetadata = FileMetadata(
		takenDate = getDateFromMetadata(path),
		createdDate = getModificationDate(path),
		nameDate = getDateFromFileName(path),
	)

	private fun getModificationDate(path: Path): ZonedDateTime{
		val date = Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.systemDefault())
		if(date.year <= 1970){
			return date.withYear(LocalDateTime.now().year)
		}
		return date
	}

	private fun getDateFromMetadata(path: Path): ZonedDateTime?{
		try{
			val metadata = ImageMetadataReader.readMetadata(path.toFile()) ?: return null
			val timeZone = TimeZone.getTimeZone(getZoneIdFromMetadata(metadata) ?: ZoneId.systemDefault())

			for(extractor in mediaDateExtractors){
				for(directory in metadata.getDirectoriesOfType(extractor.directoryClass)){
					try{
						log.debug { "Trying ${extractor.directoryClass.name}" }
						val date = extractor.parse(directory, timeZone) ?: continue
						if(date.year < 1970){
							log.warn { "Invalid year with metadata directory ${extractor.directoryClass.name} for file $path" }
							continue
						}
						log.debug { "Matched metadata directory $directory" }
						return date
					}
					catch(e: Exception){
						log.error { "Error processing metadata directory ${extractor.directoryClass.name} for $path => ${e.message}" }
					}
				}
			}
		}
		catch(e: Exception){
			log.error { "Error getting date from metadata for $path => ${e.message}" }
		}
		return null
	}

	private fun getDateFromFileName(path: Path): ZonedDateTime?{
		val fileName = path.fileName.toString()
		for(extractor in nameDateExtractors){
			try{
				log.debug { "Trying name extractor `$extractor`" }
				val date = extractor.parse(fileName) ?: continue
				if(date.year < 1970){
					log.warn { "Invalid year with used format for file $path" }
					continue
				}
				log.debug { "Matched date format for $path" }
				return date
			}
			catch(_: DateTimeParseException){
			}
			catch(e: Exception){
				log.error { "Error using format $extractor => ${e.message}" }
			}
		}
		return null
	}

	private fun getZoneIdFromMetadata(metadata: Metadata): ZoneId?{
		try{
			for(directory in metadata.getDirectoriesOfType(GpsDirectory::class.java)){
				val location = directory.geoLocation ?: continue
				timeZoneResolver.resolve(location.latitude, location.longitude)?.let { return it }
			}
			for(directory in metadata.getDirectoriesOfType(QuickTimeMetadataDirectory::class.java)){
				val representation = directory.getString(QUICKTIME_LOCATION_TAG)
				if(representation.isNullOrBlank()){
					continue
				}
				val location = PointLocationParser.parsePointLocation(representation)
				timeZoneResolver.resolve(location.latitude.degrees, location.longitude.degrees)?.let { return it }
			}
			for(directory in metadata.getDirectoriesOfType(XmpDirectory::class.java)){
				val values = directory.xmpProperties
				val latitude = values["exif:GPSLatitude"]?.let(::parseAngle) ?: continue
				val longitude = values["exif:GPSLongitude"]?.let(::parseAngle) ?: continue
				timeZoneResolver.resolve(latitude, longitude)?.let { return it }
			}
		}
		catch(e: Exception){
			log.warn(e) { "Error getting GPS infos" }
		}
		return null
	}

	companion object{
		private const val QUICKTIME_LOCATION_TAG = 0x050D
		private val ANGLE_PATTERN = Pattern.compile("""(\d{1,3}),(\d{1,2})\.(\d+)([NESW])""")

		/**
		 * Converts an XMP coordinate such as `48,51.2345N` to signed degrees.
		 */
		internal fun parseAngle(value: String): Double?{
			val matcher = ANGLE_PATTERN.matcher(value)
			if(!matcher.matches()){
				return null
			}
			val angle = matcher.group(1).toInt() +
					matcher.group(2).toInt() / 60.0 +
					"0.${matcher.group(3)}".toDouble() / 60.0
			return when(matcher.group(4)){
				"W", "S" -> -angle
				else -> angle
			}
		}
	}
}
