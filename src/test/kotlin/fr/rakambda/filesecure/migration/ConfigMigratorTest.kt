package fr.rakambda.filesecure.migration

import fr.rakambda.filesecure.config.ConflictStrategy
import fr.rakambda.filesecure.config.Configuration
import fr.rakambda.filesecure.config.Operation
import fr.rakambda.filesecure.conversion.LengthMoreThan
import fr.rakambda.filesecure.conversion.ProcessorType
import fr.rakambda.filesecure.folder.DeleteChildren
import fr.rakambda.filesecure.folder.DeleteIfEmpty
import fr.rakambda.filesecure.stage.Convert
import fr.rakambda.filesecure.stage.FolderCanonical
import fr.rakambda.filesecure.stage.FolderPerMonth
import fr.rakambda.filesecure.stage.FolderPerYear
import fr.rakambda.filesecure.stage.IfOlderThan
import fr.rakambda.filesecure.stage.Lowercase
import fr.rakambda.filesecure.stage.NameFilter
import fr.rakambda.filesecure.stage.NoRename
import fr.rakambda.filesecure.stage.RenameWithDate
import fr.rakambda.filesecure.stage.RenameWithDatePattern
import fr.rakambda.filesecure.stage.UnhandledStrategy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ConfigMigratorTest{
	private val migrator = ConfigMigrator()

	private fun migrate(vararg sources: Pair<String, String>): Configuration{
		return Configuration.parse(migrator.migrate(sources.map { (name, json) -> ConfigMigrator.Source(name, ConfigMigrator.MAPPER.readTree(json)) }))
	}

	@Test
	fun migratesFileSecureReadmeExample(){
		val configuration = migrate("old.json" to """
			{
			  "rules": [
			    {"mappings": [{"input": "/Input", "output": "/Output"}]},
			    {
			      "operation": "move",
			      "depth": 2,
			      "filters": [".*\\.png", ".*\\.jpg"],
			      "excludes": [".*\\.ini"],
			      "mappings": [{"input": "/Input2", "output": "/Output2"}]
			    }
			  ]
			}
		""".trimIndent())

		val (first, second) = configuration.pipelines
		assertThat(first.operation).isEqualTo(Operation.NONE)
		assertThat(first.stages).isEmpty()
		assertThat(second.operation).isEqualTo(Operation.MOVE)
		assertThat(second.mappings.single().input).isEqualTo(Path.of("/Input2"))
		val filter = second.stages.single() as NameFilter
		assertThat(filter.includes.map { it.pattern() }).containsExactly(""".*\.png""", """.*\.jpg""")
		assertThat(filter.excludes.map { it.pattern() }).containsExactly(""".*\.ini""")
		assertThat(second.scan.maxDepth).describedAs("depth was never read").isEqualTo(Int.MAX_VALUE)
		assertThat(migrator.warnings).anyMatch { "'depth'" in it }
	}

	@Test
	fun migratesTransformersInPriorityOrder(){
		val configuration = migrate("old.json" to """
			{
			  "rules": [{
			    "operation": "COPY",
			    "maxDepth": 3,
			    "folderExcludes": ["tmp"],
			    "skipIfAlreadyExists": true,
			    "setFileCreationDate": true,
			    "mappings": [{"input": "/in", "output": "/out"}],
			    "fileTransformers": [
			      {"name": "LowercaseTransformer"},
			      {"name": "FolderPerYearTransformer"},
			      {"name": "FolderCanonicalTransformer", "elementCount": 2},
			      {"name": "FolderPerMonthTransformer"},
			      {"name": "RenameWithDateTransformer", "format": "yyyyMMdd", "zone": "UTC"},
			      {"name": "IfOlderThanTransformer", "dayOffset": 2}
			    ],
			    "folderTransformers": [
			      {"name": "DeleteIfEmptyTransformer"},
			      {"name": "DeleteChildrenTransformer", "names": ["Thumbs.db"]}
			    ]
			  }]
			}
		""".trimIndent())

		val pipeline = configuration.pipelines.single()
		assertThat(pipeline.conflict).isEqualTo(ConflictStrategy.SKIP)
		assertThat(pipeline.setFileCreationDate).isTrue()
		assertThat(pipeline.scan.maxDepth).isEqualTo(3)
		assertThat(pipeline.scan.excludedFolders.single().pattern()).isEqualTo("tmp")
		assertThat(pipeline.stages.map { it::class }).containsExactly(
			IfOlderThan::class,
			RenameWithDate::class,
			FolderCanonical::class,
			FolderPerMonth::class,
			FolderPerYear::class,
			Lowercase::class,
		)
		assertThat((pipeline.stages[0] as IfOlderThan).dayOffset).isEqualTo(2)
		assertThat((pipeline.stages[1] as RenameWithDate).format).isEqualTo("yyyyMMdd")
		assertThat((pipeline.stages[2] as FolderCanonical).elementCount).isEqualTo(2)
		assertThat(pipeline.folderActions).containsExactly(DeleteChildren(listOf("Thumbs.db")), DeleteIfEmpty())
		assertThat(migrator.warnings).anyMatch { "unspecified order" in it }.anyMatch { "DeleteIfEmpty" in it }
	}

	@Test
	fun migratesMediaConverterReadmeExample(){
		val configuration = migrate("mc.json" to """
			{
			  "conversions": [
			    {
			      "input": "/path/to/input",
			      "output": "/path/to/output",
			      "database": "/path/to/database.db",
			      "temp": "/path/to/temp",
			      "deleteInput": false,
			      "excluded": ["subfolder/to/exclude", "another/too.d"],
			      "processors": ["VIDEO_TO_HEVC_MKV", "MP3_TO_AAC"],
			      "extensions": ["mp4", "mts", "mp3"],
			      "filters": [{"name": "LengthMoreThan", "value": 50}]
			    },
			    {"input": "/a", "output": "/b", "enabled": false}
			  ]
			}
		""".trimIndent())

		val (first, second) = configuration.pipelines
		assertThat(first.operation).isEqualTo(Operation.COPY)
		assertThat(first.conflict).isEqualTo(ConflictStrategy.SKIP)
		assertThat(first.temp).isEqualTo(Path.of("/path/to/temp"))
		assertThat(first.scan.skipHidden).isTrue()
		val excluded = first.scan.excludedFolders
		assertThat(excluded.map { it.matcher("another/too.d").matches() }).containsExactly(false, true)
		assertThat(excluded.map { it.matcher("another/tooxd").matches() }).describedAs("dots are literal").containsExactly(false, false)
		assertThat(first.stages.single()).isEqualTo(Convert(
			processors = listOf(ProcessorType.VIDEO_TO_HEVC_MKV, ProcessorType.MP3_TO_AAC),
			extensions = setOf("mp4", "mts", "mp3"),
			filters = listOf(LengthMoreThan(50)),
			unhandled = UnhandledStrategy.SKIP,
			cache = Path.of("/path/to/database.db"),
		))
		assertThat(second.enabled).isFalse()
		assertThat(second.operation).describedAs("deleteInput defaults to true").isEqualTo(Operation.MOVE)
		assertThat(second.stages.single()).isEqualTo(Convert(unhandled = UnhandledStrategy.SKIP))
	}

	@Test
	fun mergesSeveralFiles(){
		val configuration = migrate(
			"fs.json" to """{"rules": [{"operation": "COPY", "mappings": [{"input": "/a", "output": "/b"}]}]}""",
			"mc.json" to """{"conversions": [{"input": "/c", "output": "/d"}]}""",
		)

		assertThat(configuration.pipelines.map { it.name }).containsExactly("fs.json rule #1", "mc.json conversion #1")
	}

	@Test
	fun rejectsUnknownFormat(){
		assertThatThrownBy { migrate("x.json" to """{"pipelines": []}""") }.isInstanceOf(IllegalArgumentException::class.java)
	}

	@Test
	fun dropsUnknownTransformers(){
		val configuration = migrate("old.json" to """{"rules": [{"operation": "COPY", "mappings": [{"input": "/a", "output": "/b"}], "fileTransformers": [{"name": "Nope"}]}]}""")

		assertThat(configuration.pipelines.single().stages).isEmpty()
		assertThat(migrator.warnings).anyMatch { "'Nope'" in it }
	}

	@Test
	fun dropsNoRenameWhenNothingRenames(){
		val configuration = migrate("old.json" to """
			{
			  "rules": [{
			    "operation": "MOVE",
			    "mappings": [{"input": "/a", "output": "/b"}],
			    "fileTransformers": [{"name": "NoRenameTransformer"}, {"name": "FolderPerYearTransformer"}]
			  }]
			}
		""".trimIndent())

		assertThat(configuration.pipelines.single().stages.map { it::class }).containsExactly(FolderPerYear::class)
		assertThat(migrator.warnings).anyMatch { "NoRename dropped" in it }
	}

	@Test
	fun keepsNoRenameAfterRename(){
		val configuration = migrate("old.json" to """
			{
			  "rules": [
			    {
			      "operation": "MOVE",
			      "mappings": [{"input": "/a", "output": "/b"}],
			      "fileTransformers": [{"name": "NoRenameTransformer"}, {"name": "RenameWithDateTransformer", "format": "yyyyMMdd"}]
			    },
			    {
			      "operation": "MOVE",
			      "mappings": [{"input": "/c", "output": "/d"}],
			      "fileTransformers": [{"name": "NoRenameTransformer"}, {"name": "RenameWithDatePatternTransformer", "outputFormat": "yyyyMMdd", "regex": "(?<date>.*) (?<time>.*)", "dateFormat": "yyyyMMdd", "timeFormat": "HHmmss"}]
			    }
			  ]
			}
		""".trimIndent())

		val (first, second) = configuration.pipelines
		assertThat(first.stages.map { it::class }).containsExactly(RenameWithDate::class, NoRename::class)
		assertThat(second.stages.map { it::class }).containsExactly(RenameWithDatePattern::class, NoRename::class)
		assertThat(migrator.warnings).noneMatch { "NoRename dropped" in it }
	}
}
