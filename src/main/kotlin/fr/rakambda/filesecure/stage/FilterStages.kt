package fr.rakambda.filesecure.stage

import fr.rakambda.filesecure.pipeline.FileItem
import java.time.ZonedDateTime
import java.util.regex.Pattern

/**
 * Keeps files whose name fully matches one of the includes (all files if empty) and none of the excludes.
 */
data class NameFilter(
	val includes: List<Pattern> = emptyList(),
	val excludes: List<Pattern> = emptyList(),
) : SimpleStage{
	override fun process(item: FileItem): StageResult{
		val name = item.source.fileName.toString()
		if(excludes.any { it.matcher(name).matches() }){
			return StageResult.Skip("name is excluded")
		}
		if(includes.isNotEmpty() && includes.none { it.matcher(name).matches() }){
			return StageResult.Skip("name is not included")
		}
		return StageResult.Continue
	}
}

/**
 * Keeps files whose date is older than the given offset.
 */
data class IfOlderThan(
	val dayOffset: Long = 0,
	val minuteOffset: Long = 0,
) : SimpleStage{
	override fun process(item: FileItem): StageResult{
		val limit = ZonedDateTime.now().minusDays(dayOffset).minusMinutes(minuteOffset)
		if(item.metadata.date.isAfter(limit)){
			return StageResult.Skip("file is too recent")
		}
		return StageResult.Continue
	}
}
