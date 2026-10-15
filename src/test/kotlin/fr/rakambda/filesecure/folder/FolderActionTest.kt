package fr.rakambda.filesecure.folder

import fr.rakambda.filesecure.pipeline.FileOperations
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit

class FolderActionTest{
	@TempDir
	lateinit var root: Path

	private val fileOperations = FileOperations(false)

	@Test
	fun deleteIfEmptyRespectsDepth(){
		val level1 = Files.createDirectories(root.resolve("a"))
		val level2 = Files.createDirectories(level1.resolve("b"))
		val action = DeleteIfEmpty(depth = 2)

		action.apply(fileOperations, level2, root)
		action.apply(fileOperations, level1, root)

		assertThat(level2).doesNotExist()
		assertThat(level1).exists()
	}

	@Test
	fun deleteIfEmptyNeverDeletesInputWithDefaultDepth(){
		DeleteIfEmpty().apply(fileOperations, root, root)

		assertThat(root).exists()
	}

	@Test
	fun deleteIfEmptyKeepsNonEmptyFolders(){
		val folder = Files.createDirectories(root.resolve("a"))
		Files.createFile(folder.resolve("file"))

		DeleteIfEmpty().apply(fileOperations, folder, root)

		assertThat(folder).exists()
	}

	@Test
	fun deleteIfEmptyDoesNothingInDryRun(){
		val folder = Files.createDirectories(root.resolve("a"))

		DeleteIfEmpty().apply(FileOperations(true), folder, root)

		assertThat(folder).exists()
	}

	@Test
	fun deleteIfOlderThan(){
		val old = Files.createDirectories(root.resolve("old"))
		val recent = Files.createDirectories(root.resolve("recent"))
		Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS)))
		val action = DeleteIfOlderThan(dayOffset = 2)

		action.apply(fileOperations, old, root)
		action.apply(fileOperations, recent, root)

		assertThat(old).doesNotExist()
		assertThat(recent).exists()
	}

	@Test
	fun deleteChildren(){
		val folder = Files.createDirectories(root.resolve("a"))
		Files.createFile(folder.resolve("Thumbs.db"))
		Files.createFile(folder.resolve("keep.jpg"))

		DeleteChildren(listOf("Thumbs.db", "desktop.ini")).apply(fileOperations, folder, root)

		assertThat(folder.resolve("Thumbs.db")).doesNotExist()
		assertThat(folder.resolve("keep.jpg")).exists()
	}
}
