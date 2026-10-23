package fr.rakambda.filesecure.config

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import fr.rakambda.filesecure.conversion.LengthMoreThan
import fr.rakambda.filesecure.conversion.ProcessorType
import fr.rakambda.filesecure.folder.DeleteChildren
import fr.rakambda.filesecure.folder.DeleteIfEmpty
import fr.rakambda.filesecure.stage.Convert
import fr.rakambda.filesecure.stage.FolderPerMonth
import fr.rakambda.filesecure.stage.FolderPerYear
import fr.rakambda.filesecure.stage.NameFilter
import fr.rakambda.filesecure.stage.RenameWithDate
import fr.rakambda.filesecure.stage.UnhandledStrategy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class ConfigurationTest{
	@Test
	fun parsesFullPipeline(){
		val configuration = Configuration.parse("""
			# Comments are allowed
			pipelines:
			  - name: Phone
			    mappings:
			      - input: /in
			        output: /out
			    scan:
			      maxDepth: 3
			      skipHidden: true
			      excludedFolders: ['\.thumbnails']
			    stages:
			      - type: NameFilter
			        includes: ['.*\.mp4']
			      - type: Convert
			        processors: [video_to_hevc_mkv]
			        filters:
			          - type: LengthMoreThan
			            value: 50
			        unhandled: PASS
			        cache: /db/cache
			      - type: FolderPerMonth
			      - type: RenameWithDate
			        format: yyyy-MM-dd
			    operation: move
			    conflict: skip
			    setFileCreationDate: true
			    folderActions:
			      - type: DeleteIfEmpty
			        depth: 2
		""".trimIndent())

		val pipeline = configuration.pipelines.single()
		assertThat(pipeline.name).isEqualTo("Phone")
		assertThat(pipeline.mappings).containsExactly(Mapping(Path.of("/in"), Path.of("/out")))
		assertThat(pipeline.scan.maxDepth).isEqualTo(3)
		assertThat(pipeline.scan.skipHidden).isTrue()
		assertThat(pipeline.scan.excludedFolders.single().pattern()).isEqualTo("""\.thumbnails""")
		assertThat(pipeline.stages).hasSize(4)
		assertThat(pipeline.stages[0]).isInstanceOf(NameFilter::class.java)
		assertThat(pipeline.stages[1]).isEqualTo(Convert(
			processors = listOf(ProcessorType.VIDEO_TO_HEVC_MKV),
			filters = listOf(LengthMoreThan(50)),
			unhandled = UnhandledStrategy.PASS,
			cache = Path.of("/db/cache"),
		))
		assertThat(pipeline.stages[2]).isInstanceOf(FolderPerMonth::class.java)
		assertThat((pipeline.stages[3] as RenameWithDate).format).isEqualTo("yyyy-MM-dd")
		assertThat(pipeline.operation).isEqualTo(Operation.MOVE)
		assertThat(pipeline.conflict).isEqualTo(ConflictStrategy.SKIP)
		assertThat(pipeline.setFileCreationDate).isTrue()
		assertThat(pipeline.folderActions).containsExactly(DeleteIfEmpty(2))
	}

	@Test
	fun appliesDefaults(){
		val pipeline = Configuration.parse("""{"pipelines": [{"mappings": [{"input": "/in", "output": "/out"}]}]}""").pipelines.single()

		assertThat(pipeline.enabled).isTrue()
		assertThat(pipeline.operation).isEqualTo(Operation.NONE)
		assertThat(pipeline.conflict).isEqualTo(ConflictStrategy.RENAME)
		assertThat(pipeline.scan.maxDepth).isEqualTo(Int.MAX_VALUE)
		assertThat(pipeline.stages).isEmpty()
	}

	@Test
	fun readsWindowsPathsOnAnyPlatform(){
		val pipeline = Configuration.parse("""{"pipelines": [{"mappings": [{"input": "D:/Documents/In", "output": "//nas.home/share"}]}]}""").pipelines.single()
		
		assertThat(pipeline.mappings.single().input).isEqualTo(Path.of("D:/Documents/In"))
	}
	
	@Test
	fun loadsJsonFilesWithJsonParser(@TempDir directory: Path){
		val file = directory.resolve("config.json")
		file.writeText("{\n\t// Tabs, comments and trailing commas\n\t\"pipelines\": [{\"mappings\": [{\"input\": \"/in\", \"output\": \"/out\"}],}],\n}")
		
		assertThat(Configuration.load(file).pipelines).hasSize(1)
	}
	
	@Test
	fun loadsYamlFiles(@TempDir directory: Path){
		val file = directory.resolve("config.yaml")
		file.writeText("pipelines:\n  - mappings:\n      - input: D:/in\n        output: //nas/out\n")
		
		assertThat(Configuration.load(file).pipelines.single().mappings.single().output).isEqualTo(Path.of("//nas/out"))
	}
	
	@Test
	fun rejectsUnknownProperties(){
		assertThatThrownBy { Configuration.parse("""{"pipelines": [{"mappings": [], "depth": 2}]}""") }
			.isInstanceOf(UnrecognizedPropertyException::class.java)
	}

	@Test
	fun rejectsSeveralConversions(){
		assertThatThrownBy {
			Configuration.parse("""{"pipelines": [{"mappings": [{"input": "/in", "output": "/out"}], "stages": [{"type": "Convert"}, {"type": "Convert"}]}]}""")
		}.isInstanceOf(InvalidConfigurationException::class.java)
	}

	@Test
	fun rejectsPipelineWithoutMapping(){
		assertThatThrownBy { Configuration.parse("""{"pipelines": [{}]}""") }
			.isInstanceOf(InvalidConfigurationException::class.java)
	}

	@Test
	fun prependsDefaultStagesAndFolderActions(){
		val pipelines = Configuration.parse("""
			defaults:
			  stages:
			    - type: NameFilter
			      excludes: ['.*\.db']
			    - type: FolderPerYear
			  folderActions:
			    - type: DeleteChildren
			      names: [Thumbs.db]
			pipelines:
			  - mappings: [{input: /a, output: /b}]
			    stages:
			      - type: RenameWithDate
			    folderActions:
			      - type: DeleteIfEmpty
			  - mappings: [{input: /c, output: /d}]
		""".trimIndent()).pipelines
		
		assertThat(pipelines[0].stages.map { it::class }).containsExactly(NameFilter::class, FolderPerYear::class, RenameWithDate::class)
		assertThat((pipelines[0].stages[0] as NameFilter).excludes.map { it.pattern() }).containsExactly(""".*\.db""")
		assertThat(pipelines[0].folderActions).containsExactly(DeleteChildren(listOf("Thumbs.db")), DeleteIfEmpty())
		assertThat(pipelines[1].stages.map { it::class }).containsExactly(NameFilter::class, FolderPerYear::class)
		assertThat(pipelines[1].folderActions).containsExactly(DeleteChildren(listOf("Thumbs.db")))
	}
	
	@Test
	fun mergesDefaultScanKeyByKey(){
		val pipelines = Configuration.parse("""
			defaults:
			  scan:
			    skipHidden: true
			    maxDepth: 2
			pipelines:
			  - mappings: [{input: /a, output: /b}]
			    scan:
			      maxDepth: 5
			      excludedFolders: [tmp]
			  - mappings: [{input: /c, output: /d}]
		""".trimIndent()).pipelines
		
		assertThat(pipelines[0].scan.skipHidden).isTrue()
		assertThat(pipelines[0].scan.maxDepth).isEqualTo(5)
		assertThat(pipelines[0].scan.excludedFolders.map { it.pattern() }).containsExactly("tmp")
		assertThat(pipelines[1].scan).isEqualTo(ScanOptions(maxDepth = 2, skipHidden = true))
	}
	
	@Test
	fun usesDefaultValuesOnlyWhenPipelineDoesNotSetThem(){
		val pipelines = Configuration.parse("""
			defaults:
			  operation: MOVE
			  conflict: SKIP
			  setFileCreationDate: true
			  temp: /temp
			pipelines:
			  - mappings: [{input: /a, output: /b}]
			  - mappings: [{input: /c, output: /d}]
			    operation: COPY
			    conflict: RENAME
			    setFileCreationDate: false
			    temp: /other
		""".trimIndent()).pipelines
		
		assertThat(pipelines[0].operation).isEqualTo(Operation.MOVE)
		assertThat(pipelines[0].conflict).isEqualTo(ConflictStrategy.SKIP)
		assertThat(pipelines[0].setFileCreationDate).isTrue()
		assertThat(pipelines[0].temp).isEqualTo(Path.of("/temp"))
		assertThat(pipelines[1].operation).isEqualTo(Operation.COPY)
		assertThat(pipelines[1].conflict).isEqualTo(ConflictStrategy.RENAME)
		assertThat(pipelines[1].setFileCreationDate).isFalse()
		assertThat(pipelines[1].temp).isEqualTo(Path.of("/other"))
	}
	
	@Test
	fun ignoresDefaultsWhenPipelineOptsOut(){
		val pipelines = Configuration.parse("""
			defaults:
			  operation: MOVE
			  stages:
			    - type: FolderPerYear
			pipelines:
			  - mappings: [{input: /a, output: /b}]
			    useDefaults: false
			    stages:
			      - type: RenameWithDate
			  - mappings: [{input: /c, output: /d}]
			    useDefaults: true
		""".trimIndent()).pipelines
		
		assertThat(pipelines[0].operation).isEqualTo(Operation.NONE)
		assertThat(pipelines[0].stages.map { it::class }).containsExactly(RenameWithDate::class)
		assertThat(pipelines[1].operation).isEqualTo(Operation.MOVE)
		assertThat(pipelines[1].stages.map { it::class }).containsExactly(FolderPerYear::class)
	}
	
	@Test
	fun rejectsPipelineOnlyKeysInDefaults(){
		listOf("name: x", "enabled: false", "mappings: []", "useDefaults: false").forEach { entry ->
			assertThatThrownBy { Configuration.parse("defaults:\n  $entry\npipelines: []") }
				.describedAs(entry)
				.isInstanceOf(InvalidConfigurationException::class.java)
				.hasMessageContaining(entry.substringBefore(':'))
		}
	}
	
	@Test
	fun rejectsUnknownPropertiesInDefaults(){
		assertThatThrownBy { Configuration.parse("defaults:\n  operaton: MOVE\npipelines: []") }
			.isInstanceOf(UnrecognizedPropertyException::class.java)
			.hasMessageContaining("operaton")
			.hasMessageContaining("defaults")
		assertThatThrownBy { Configuration.parse("defaults:\n  stages:\n    - type: NameFilter\n      exclude: [a]\npipelines: []") }
			.isInstanceOf(UnrecognizedPropertyException::class.java)
	}
	
	@Test
	fun rejectsUseDefaultsOutsidePipelines(){
		assertThatThrownBy { Configuration.parse("useDefaults: false\npipelines: []") }
			.isInstanceOf(UnrecognizedPropertyException::class.java)
		assertThatThrownBy { Configuration.parse("pipelines:\n  - mappings: [{input: /a, output: /b}]\n    scan:\n      useDefaults: false") }
			.isInstanceOf(UnrecognizedPropertyException::class.java)
		assertThatThrownBy { Configuration.parse("pipelines:\n  - mappings: [{input: /a, output: /b}]\n    useDefaults: maybe") }
			.isInstanceOf(InvalidConfigurationException::class.java)
	}
	
	@Test
	fun leavesConfigurationWithoutDefaultsUnchanged(){
		val content = """
			pipelines:
			  - name: A
			    mappings: [{input: /a, output: /b}]
			    scan:
			      skipHidden: true
			    stages:
			      - type: FolderPerYear
			    operation: COPY
		""".trimIndent()
		
		val pipeline = Configuration.parse(content).pipelines.single()
		
		assertThat(pipeline.copy(stages = emptyList())).isEqualTo(Pipeline(
			name = "A",
			mappings = listOf(Mapping(Path.of("/a"), Path.of("/b"))),
			scan = ScanOptions(skipHidden = true),
			operation = Operation.COPY,
		))
		assertThat(pipeline.stages.single()).isInstanceOf(FolderPerYear::class.java)
	}

	@Test
	fun resolvesAliasedStage(){
		val pipelines = Configuration.parse("""
			pipelines:
			  - mappings: [{input: /a, output: /b}]
			    stages:
			      - &rename
			        type: RenameWithDate
			        format: yyyy-MM-dd
			  - mappings: [{input: /c, output: /d}]
			    stages:
			      - type: FolderPerYear
			      - *rename
		""".trimIndent()).pipelines
		
		assertThat((pipelines[0].stages.single() as RenameWithDate).format).isEqualTo("yyyy-MM-dd")
		assertThat((pipelines[1].stages[1] as RenameWithDate).format).isEqualTo("yyyy-MM-dd")
	}
	
	@Test
	fun resolvesAliasedStageListDefinedInTemplates(){
		val pipelines = Configuration.parse("""
			templates:
			  sortByYear: &sortByYear
			    - type: FolderPerYear
			    - type: RenameWithDate
			  anything: [1, {free: form}]
			pipelines:
			  - mappings: [{input: /a, output: /b}]
			    stages: *sortByYear
			  - mappings: [{input: /c, output: /d}]
			    stages: *sortByYear
		""".trimIndent()).pipelines
		
		assertThat(pipelines).allSatisfy { assertThat(it.stages.map { stage -> stage::class }).containsExactly(FolderPerYear::class, RenameWithDate::class) }
	}
	
	@Test
	fun mergesAnchoredMappingIntoPipeline(){
		val pipelines = Configuration.parse("""
			templates:
			  archive: &archive
			    operation: COPY
			    conflict: SKIP
			    folderActions:
			      - type: DeleteIfEmpty
			pipelines:
			  - <<: *archive
			    mappings: [{input: /a, output: /b}]
			  - <<: *archive
			    mappings: [{input: /c, output: /d}]
			    conflict: RENAME
		""".trimIndent()).pipelines
		
		assertThat(pipelines.map { it.operation }).containsExactly(Operation.COPY, Operation.COPY)
		assertThat(pipelines.map { it.conflict }).containsExactly(ConflictStrategy.SKIP, ConflictStrategy.RENAME)
		assertThat(pipelines).allSatisfy { assertThat(it.folderActions).containsExactly(DeleteIfEmpty()) }
	}
	
	@Test
	fun ignoresTemplatesOnlyAtTopLevel(){
		assertThat(Configuration.parse("templates: {a: b}\npipelines: []").pipelines).isEmpty()
		assertThatThrownBy { Configuration.parse("pipelines:\n  - mappings: [{input: /a, output: /b}]\n    templates: {a: b}") }
			.isInstanceOf(UnrecognizedPropertyException::class.java)
	}
	
	@Test
	fun keepsYamlScalarsAsText(){
		val pipeline = Configuration.parse("""
			pipelines:
			  - name: no
			    mappings: [{input: /a, output: /b}]
			    stages:
			      - type: NameFilter
			        excludes: [on, 2023-05-12, 0755]
		""".trimIndent()).pipelines.single()
		
		assertThat(pipeline.name).isEqualTo("no")
		assertThat((pipeline.stages.single() as NameFilter).excludes.map { it.pattern() }).containsExactly("on", "2023-05-12", "0755")
	}
	
	@Test
	fun reportsLineOfInvalidValue(){
		assertThatThrownBy {
			Configuration.parse("""
				pipelines:
				  - mappings: [{input: /a, output: /b}]
				    stages:
				      - type: FolderPerYear
				      - typo: x
			""".trimIndent())
		}.hasMessageContaining("line: 5").hasMessageContaining("pipelines")
		assertThatThrownBy { Configuration.parse("pipelines:\n  - name: a\n  - mappings: [{input: /a, output: /b}]\n    depth: 2") }
			.hasMessageContaining("line: 3")
			.hasMessageContaining("depth")
	}
	
	@Test
	fun rejectsDuplicateKeys(){
		assertThatThrownBy { Configuration.parse("pipelines: []\npipelines: []") }
			.isInstanceOf(InvalidConfigurationException::class.java)
			.hasMessageContaining("line 2")
	}
}
