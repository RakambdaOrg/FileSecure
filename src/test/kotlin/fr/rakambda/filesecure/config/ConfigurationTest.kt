package fr.rakambda.filesecure.config

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import fr.rakambda.filesecure.conversion.LengthMoreThan
import fr.rakambda.filesecure.conversion.ProcessorType
import fr.rakambda.filesecure.folder.DeleteIfEmpty
import fr.rakambda.filesecure.stage.Convert
import fr.rakambda.filesecure.stage.FolderPerMonth
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
}
