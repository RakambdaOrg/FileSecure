package fr.rakambda.filesecure.metadata

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

/**
 * Finds a date in a file name with a regex, the matched group being parsed with a date format.
 */
class NameDateExtractor(
	private val pattern: Pattern,
	format: String,
	private val group: Int = 0,
){
	private val formatter = DateTimeFormatter.ofPattern(format)
		.withLocale(Locale.ENGLISH)
		.withZone(ZoneId.systemDefault())

	/**
	 * @throws java.time.format.DateTimeParseException If the matched text is not a valid date.
	 */
	fun parse(name: String): ZonedDateTime?{
		val matcher = pattern.matcher(name)
		if(!matcher.find()){
			return null
		}
		return ZonedDateTime.parse(matcher.group(group), formatter)
	}

	override fun toString(): String = "NameDateExtractor(${pattern.pattern()})"

	companion object{
		val DEFAULTS = listOf(
			NameDateExtractor(Pattern.compile("""\d{4}-\d{2}-\d{2} \d{2}\.\d{2}\.\d{2}"""), "yyyy-MM-dd HH.mm.ss"),
			NameDateExtractor(Pattern.compile("""\d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2}"""), "yyyy-MM-dd HH-mm-ss"),
			NameDateExtractor(Pattern.compile("""\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}"""), "yyyy-MM-dd_HH-mm-ss"),
			NameDateExtractor(Pattern.compile("""\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}"""), "yyyy-MM-dd-HH-mm-ss"),
			NameDateExtractor(Pattern.compile("""IMG_(\d{8}_\d{6})"""), "yyyyMMdd_HHmmss", 1),
			NameDateExtractor(Pattern.compile("""Video-(\d{8}-\d{6})"""), "yyyyMMdd-HHmmss", 1),
		)
	}
}
