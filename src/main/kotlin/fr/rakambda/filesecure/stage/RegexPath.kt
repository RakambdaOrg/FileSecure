package fr.rakambda.filesecure.stage

import fr.rakambda.filesecure.pipeline.FileItem
import java.util.regex.Pattern

/**
 * Rewrites the destination relative to the output folder (using `/` as separator) with the first rule whose regex fully matches it.
 *
 * For example the rule `(?:[^/]* - )?(\d+)/(.*)` → `Whatsapp/$1/$2` moves `Bob - 123/a.jpg` to `Whatsapp/123/a.jpg`.
 */
data class RegexPath(
	val rules: List<Rule>,
	/**
	 * What happens to files matching no rule.
	 */
	val unmatched: UnhandledStrategy = UnhandledStrategy.SKIP,
) : SimpleStage{
	override fun process(item: FileItem): StageResult{
		val relative = item.relativeDestination.toString().replace("\\", "/")
		for(rule in rules){
			val matcher = rule.regex.matcher(relative)
			if(!matcher.matches()){
				continue
			}
			val replaced = item.outputBase.resolve(matcher.replaceFirst(rule.replacement)).normalize()
			if(!replaced.startsWith(item.outputBase.normalize())){
				return StageResult.Skip("rewritten destination $replaced is outside the output folder")
			}
			item.destination = replaced
			return StageResult.Continue
		}
		return when(unmatched){
			UnhandledStrategy.PASS -> StageResult.Continue
			UnhandledStrategy.SKIP -> StageResult.Skip("path matches no rule")
		}
	}
	
	data class Rule(
		val regex: Pattern,
		/**
		 * Replacement where `$1` or `${name}` refer to the groups of the regex.
		 */
		val replacement: String,
	)
}
