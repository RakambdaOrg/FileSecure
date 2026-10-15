package fr.rakambda.filesecure.metadata

import com.drew.metadata.Directory
import com.drew.metadata.xmp.XmpDirectory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Reads a date from one kind of metadata directory.
 */
sealed interface MediaDateExtractor{
	val directoryClass: Class<out Directory>

	fun parse(directory: Directory, timeZone: TimeZone): ZonedDateTime?
}

class TagMediaDateExtractor(
	override val directoryClass: Class<out Directory>,
	private val tag: Int,
) : MediaDateExtractor{
	override fun parse(directory: Directory, timeZone: TimeZone): ZonedDateTime? =
		directory.getDate(tag, timeZone)?.toInstant()?.atZone(timeZone.toZoneId())
}

object XmpMediaDateExtractor : MediaDateExtractor{
	private val KEYS = listOf("xmp:CreateDate", "photoshop:DateCreated")
	private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

	override val directoryClass = XmpDirectory::class.java

	override fun parse(directory: Directory, timeZone: TimeZone): ZonedDateTime?{
		val values = (directory as XmpDirectory).xmpProperties
		for(key in KEYS){
			val value = values[key] ?: continue
			try{
				return ZonedDateTime.parse(value, FORMATTER.withZone(timeZone.toZoneId()))
			}
			catch(_: Exception){
			}
		}
		return null
	}
}
