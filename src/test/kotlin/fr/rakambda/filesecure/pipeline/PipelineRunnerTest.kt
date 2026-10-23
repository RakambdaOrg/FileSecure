package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.ConflictStrategy
import fr.rakambda.filesecure.config.Mapping
import fr.rakambda.filesecure.config.Operation
import fr.rakambda.filesecure.config.Pipeline
import fr.rakambda.filesecure.config.ScanOptions
import fr.rakambda.filesecure.conversion.ConversionTools
import fr.rakambda.filesecure.folder.DeleteIfEmpty
import fr.rakambda.filesecure.metadata.MetadataExtractor
import fr.rakambda.filesecure.stage.FolderPerYear
import fr.rakambda.filesecure.stage.NameFilter
import fr.rakambda.filesecure.stage.RenameWithDate
import fr.rakambda.filesecure.stage.Stage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.regex.Pattern
import kotlin.io.path.createDirectories
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeText

class PipelineRunnerTest{
	@TempDir
	lateinit var root: Path

	private val input by lazy { root.resolve("in").createDirectories() }
	private val output by lazy { root.resolve("out") }

	private fun file(relative: String, content: String = relative): Path = input.resolve(relative).also {
		it.parent.createDirectories()
		it.writeText(content)
	}

	private fun run(pipeline: Pipeline, dryRun: Boolean = false) = runBlocking {
		val environment = RunEnvironment(
			fileOperations = FileOperations(dryRun),
			metadataExtractor = MetadataExtractor({ _, _ -> null }),
			tools = ConversionTools(),
			workers = 4,
		)
		PipelineRunner(environment).run(pipeline, "test")
	}

	private fun pipeline(operation: Operation, vararg stages: Stage) = Pipeline(
		mappings = listOf(Mapping(input, output)),
		operation = operation,
		stages = stages.toList(),
	)

	@Test
	fun copyKeepsStructure(){
		val a = file("a/b.txt")
		val c = file("c.txt")

		run(pipeline(Operation.COPY))

		assertThat(output.resolve("a/b.txt")).hasContent("a/b.txt")
		assertThat(output.resolve("c.txt")).hasContent("c.txt")
		assertThat(a).exists()
		assertThat(c).exists()
	}

	@Test
	fun moveRemovesSourcesAndCleansFolders(){
		val a = file("a/b.txt")

		run(pipeline(Operation.MOVE).copy(folderActions = listOf(DeleteIfEmpty())))

		assertThat(output.resolve("a/b.txt")).hasContent("a/b.txt")
		assertThat(a).doesNotExist()
		assertThat(a.parent).doesNotExist()
		assertThat(input).describedAs("input folder itself is kept").exists()
	}

	@Test
	fun noneOperationWritesNothing(){
		val a = file("a.txt")

		run(pipeline(Operation.NONE))

		assertThat(a).exists()
		assertThat(output).doesNotExist()
	}

	@Test
	fun dryRunWritesNothing(){
		val a = file("a/b.txt")

		run(pipeline(Operation.MOVE).copy(folderActions = listOf(DeleteIfEmpty())), dryRun = true)

		assertThat(a).exists()
		assertThat(output).doesNotExist()
	}

	@Test
	fun conflictRename(){
		file("a.txt", "new")
		output.createDirectories().resolve("a.txt").writeText("old")

		run(pipeline(Operation.COPY))

		assertThat(output.resolve("a.txt")).hasContent("old")
		assertThat(output.resolve("a (1).txt")).hasContent("new")
	}

	@Test
	fun conflictRenameWithoutExtension(){
		file("Makefile", "new")
		output.createDirectories().resolve("Makefile").writeText("old")

		run(pipeline(Operation.COPY))

		assertThat(output.resolve("Makefile (1)")).hasContent("new")
	}

	@Test
	fun conflictSkip(){
		val a = file("a.txt", "new")
		output.createDirectories().resolve("a.txt").writeText("old")

		run(pipeline(Operation.MOVE).copy(conflict = ConflictStrategy.SKIP))

		assertThat(output.resolve("a.txt")).hasContent("old")
		assertThat(a).exists()
		assertThat(output.resolve("a (1).txt")).doesNotExist()
	}

	@Test
	fun sameDestinationForSeveralFilesIsRenamed(){
		val date = FileTime.from(Instant.parse("2020-06-01T12:00:00Z"))
		file("x.txt", "x").setLastModifiedTime(date)
		file("y.txt", "y").setLastModifiedTime(date)

		run(pipeline(Operation.COPY, RenameWithDate(format = "yyyy", zone = "UTC")))

		assertThat(listOf(output.resolve("2020.txt").readText(), output.resolve("2020 (1).txt").readText())).containsExactlyInAnyOrder("x", "y")
	}

	@Test
	fun inPlaceMoveDoesNothing(){
		val a = file("a.txt")

		run(Pipeline(mappings = listOf(Mapping(input, input)), operation = Operation.MOVE))

		assertThat(a).hasContent("a.txt")
		assertThat(input.resolve("a (1).txt")).doesNotExist()
	}

	@Test
	fun scanOptions(){
		file("keep.txt")
		file("deep/deeper.txt")
		file("excluded/file.txt")
		file(".hidden")
		file(".hiddenFolder/file.txt")

		run(pipeline(Operation.COPY).copy(scan = ScanOptions(maxDepth = 2, skipHidden = true, excludedFolders = listOf(Pattern.compile("excl.*")))))

		assertThat(output.resolve("keep.txt")).exists()
		assertThat(output.resolve("deep/deeper.txt")).exists()
		assertThat(output.resolve("excluded")).doesNotExist()
		assertThat(output.resolve(".hidden")).doesNotExist()
		assertThat(output.resolve(".hiddenFolder")).doesNotExist()
	}

	@Test
	fun scanIgnoresPartialFiles(){
		file("a.txt")
		val partial = file("sub/.b.txt${FileOperations.PARTIAL_SUFFIX}")

		run(pipeline(Operation.MOVE))

		assertThat(output.resolve("a.txt")).exists()
		assertThat(output.resolve("sub")).doesNotExist()
		assertThat(partial).exists()
	}

	@Test
	fun maxDepthDoesNotProcessFoldersAsFiles(){
		val deep = file("deep/deeper.txt")
		file("keep.txt")

		run(pipeline(Operation.MOVE).copy(scan = ScanOptions(maxDepth = 1)))

		assertThat(output.resolve("keep.txt")).exists()
		assertThat(output.resolve("deep")).doesNotExist()
		assertThat(deep).exists()
	}

	@Test
	fun stagesRunInOrder(){
		file("2023-05-12 10.15.12.jpg")
		file("2022-01-02 03.04.05.txt")

		run(pipeline(
			Operation.COPY,
			NameFilter(includes = listOf(Pattern.compile(""".*\.jpg"""))),
			FolderPerYear(),
			RenameWithDate(format = "yyyyMMdd_HHmmss"),
		))

		assertThat(output.resolve("2023/20230512_101512.jpg")).exists()
		assertThat(output.resolve("2022")).doesNotExist()
	}

	@Test
	fun setFileCreationDate(){
		file("2023-05-12 10.15.12.txt")

		run(pipeline(Operation.MOVE).copy(setFileCreationDate = true))

		val expected = LocalDateTime.of(2023, 5, 12, 10, 15, 12).atZone(ZoneId.systemDefault()).toInstant()
		assertThat(output.resolve("2023-05-12 10.15.12.txt").getLastModifiedTime().toInstant()).isEqualTo(expected)
	}

	@Test
	fun missingInputIsSkipped(){
		Files.delete(input)

		run(pipeline(Operation.COPY))

		assertThat(output).doesNotExist()
	}
}

