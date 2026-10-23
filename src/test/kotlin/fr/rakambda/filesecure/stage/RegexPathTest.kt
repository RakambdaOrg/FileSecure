package fr.rakambda.filesecure.stage

import fr.rakambda.filesecure.config.Configuration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.regex.Pattern

class RegexPathTest{
	private val base = Path.of("/data")
	private val stage = RegexPath(listOf(
		RegexPath.Rule(Pattern.compile("""(?:[^/]* - )?(\d+)/(.*)"""), "Whatsapp/$1/$2"),
		RegexPath.Rule(Pattern.compile("""(bob_tw|carol_tw)/(.*)"""), "Z/Twitter/$1/$2"),
	))

	private fun process(stage: RegexPath, relative: String) = fileItem(base, base.resolve(relative)).let { it to stage.process(it) }

	@Test
	fun rewritesWithFirstMatchingRule(){
		val (item, result) = process(stage, "Bob Smith - 639123/sub/a.jpg")

		assertThat(result).isEqualTo(StageResult.Continue)
		assertThat(item.destination).isEqualTo(Path.of("/data/Whatsapp/639123/sub/a.jpg"))
	}

	@Test
	fun optionalPartOfRegex(){
		assertThat(process(stage, "639123/a.jpg").first.destination).isEqualTo(Path.of("/data/Whatsapp/639123/a.jpg"))
	}

	@Test
	fun secondRule(){
		assertThat(process(stage, "carol_tw/a.jpg").first.destination).isEqualTo(Path.of("/data/Z/Twitter/carol_tw/a.jpg"))
	}

	@Test
	fun skipsUnmatchedByDefault(){
		val (item, result) = process(stage, "Other/a.jpg")

		assertThat(result).isInstanceOf(StageResult.Skip::class.java)
		assertThat(item.destination).isEqualTo(Path.of("/data/Other/a.jpg"))
	}

	@Test
	fun passesUnmatchedWhenAsked(){
		assertThat(process(stage.copy(unmatched = UnhandledStrategy.PASS), "a.jpg").second).isEqualTo(StageResult.Continue)
	}

	@Test
	fun refusesToLeaveOutputFolder(){
		val escaping = RegexPath(listOf(RegexPath.Rule(Pattern.compile("(.*)"), "../$1")))

		assertThat(process(escaping, "a.jpg").second).isInstanceOf(StageResult.Skip::class.java)
	}

	@Test
	fun parsesFromConfiguration(){
		val pipeline = Configuration.parse("""
			{"pipelines": [{
				"mappings": [{"input": "/in", "output": "/out"}],
				"stages": [{"type": "RegexPath", "rules": [{"regex": "(\\d+)/(.*)", "replacement": "W/$1/$2"}], "unmatched": "PASS"}]
			}]}
		""".trimIndent()).pipelines.single()

		val parsed = pipeline.stages.single() as RegexPath
		assertThat(parsed.rules.single().regex.pattern()).isEqualTo("""(\d+)/(.*)""")
		assertThat(parsed.rules.single().replacement).isEqualTo("W/$1/$2")
		assertThat(parsed.unmatched).isEqualTo(UnhandledStrategy.PASS)
	}
}
