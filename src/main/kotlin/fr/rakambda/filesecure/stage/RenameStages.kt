package fr.rakambda.filesecure.stage

import fr.rakambda.filesecure.pipeline.FileItem
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

private val log = KotlinLogging.logger {}

/**
 * Renames the file with its date, keeping its extension.
 */
data class RenameWithDate(
	val format: String = "yyyy-MM-dd HH.mm.ss",
	val locale: String = "en",
	/**
	 * When not empty, only files whose original name fully matches one of these are renamed.
	 */
	val filters: List<Pattern> = emptyList(),
	val zone: String? = null,
) : PathStage{
	private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(format, Locale.Builder().setLanguage(locale).build())
		.withZone(zone?.let(ZoneId::of) ?: ZoneId.systemDefault())

	override fun transform(item: FileItem): Path?{
		val sourceName = item.source.fileName.toString()
		if(filters.isNotEmpty() && filters.none { it.matcher(sourceName).matches() }){
			log.debug { "Not renaming $sourceName as it doesn't match any filter" }
			return null
		}
		return item.destination.withFileName(formatter.format(item.metadata.date) + item.destination.extensionWithDot)
	}
}

/**
 * Renames the file with a date read from its current name, keeping its extension. Files whose name doesn't contain a date are skipped.
 *
 * The regex must define the named groups `date` and `time`, parsed with [dateFormat] and [timeFormat].
 */
data class RenameWithDatePattern(
	val regex: Pattern,
	val dateFormat: String,
	val timeFormat: String,
	val outputFormat: String = "yyyy-MM-dd HH.mm.ss",
	val locale: String = "en",
	val zone: String? = null,
) : SimpleStage{
	private val zoneId: ZoneId = zone?.let(ZoneId::of) ?: ZoneId.systemDefault()
	private val inputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("$dateFormat $timeFormat", Locale.Builder().setLanguage(locale).build())
		.withZone(zoneId)
	private val outputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(outputFormat, Locale.Builder().setLanguage(locale).build())
		.withZone(zoneId)

	override fun process(item: FileItem): StageResult{
		val date = extractDate(item.destination.fileName.toString()) ?: return StageResult.Skip("no date found in name")
		item.destination = item.destination.withFileName(outputFormatter.format(date) + item.destination.extensionWithDot)
		return StageResult.Continue
	}

	private fun extractDate(name: String): ZonedDateTime?{
		try{
			val matcher = regex.matcher(name)
			if(!matcher.matches()){
				return null
			}
			return ZonedDateTime.parse("${matcher.group("date")} ${matcher.group("time")}", inputFormatter)
		}
		catch(e: Exception){
			log.warn { "Failed to read a date from `$name` with format `$dateFormat $timeFormat`, the file is skipped: ${e.message}" }
			return null
		}
	}
}
