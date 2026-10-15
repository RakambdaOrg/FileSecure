package fr.rakambda.filesecure.stage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.ZonedDateTime
import java.util.regex.Pattern

class FilterStagesTest{
	private val base = Path.of("/out")

	private fun item(name: String, date: ZonedDateTime? = null) = fileItem(base, base.resolve(name), source = Path.of("/input", name), date = date)

	@Test
	fun nameFilterKeepsEverythingByDefault(){
		assertThat(NameFilter().process(item("a.txt"))).isEqualTo(StageResult.Continue)
	}

	@Test
	fun nameFilterIncludes(){
		val filter = NameFilter(includes = listOf(Pattern.compile(""".*\.png"""), Pattern.compile(""".*\.jpg""")))

		assertThat(filter.process(item("a.png"))).isEqualTo(StageResult.Continue)
		assertThat(filter.process(item("a.jpg"))).isEqualTo(StageResult.Continue)
		assertThat(filter.process(item("a.png.txt"))).isInstanceOf(StageResult.Skip::class.java)
	}

	@Test
	fun nameFilterExcludesWin(){
		val filter = NameFilter(includes = listOf(Pattern.compile(".*")), excludes = listOf(Pattern.compile(""".*\.ini""")))

		assertThat(filter.process(item("desktop.ini"))).isInstanceOf(StageResult.Skip::class.java)
		assertThat(filter.process(item("a.png"))).isEqualTo(StageResult.Continue)
	}

	@Test
	fun ifOlderThan(){
		val stage = IfOlderThan(dayOffset = 2)

		assertThat(stage.process(item("a", ZonedDateTime.now().minusDays(3)))).isEqualTo(StageResult.Continue)
		assertThat(stage.process(item("a", ZonedDateTime.now().minusDays(1)))).isInstanceOf(StageResult.Skip::class.java)
	}
}
