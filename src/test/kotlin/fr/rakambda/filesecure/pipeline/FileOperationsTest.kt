package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.Operation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class FileOperationsTest{
	@TempDir
	lateinit var root: Path

	private val input by lazy { root.resolve("in").createDirectories() }
	private val output by lazy { root.resolve("out") }

	private val copies = mutableListOf<Pair<Path, Path>>()
	private val atomicMoves = mutableListOf<Pair<Path, Path>>()

	private fun file(name: String, content: String = name): Path = input.resolve(name).also { it.writeText(content) }

	private fun realCopy(source: Path, partial: Path){
		copies += source to partial
		Files.copy(source, partial, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
	}

	private fun operations(
		crossFileSystem: Boolean = false,
		copy: (Path, Path) -> Unit = ::realCopy,
	) = FileOperations(
		false,
		{ source, destination ->
			atomicMoves += source to destination
			if(crossFileSystem){
				throw AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test")
			}
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
		},
		copy,
	)

	private fun truncatedCopy(source: Path, partial: Path){
		copies += source to partial
		val bytes = Files.readAllBytes(source)
		partial.writeBytes(bytes.copyOf(bytes.size / 2))
	}

	private fun failingCopy(source: Path, partial: Path){
		copies += source to partial
		partial.writeText("partial")
		throw IOException("Network dropped")
	}

	private fun assertNoPartial(folder: Path = output){
		if(Files.isDirectory(folder)){
			assertThat(folder.listDirectoryEntries().map { it.name }).noneMatch { it.endsWith(FileOperations.PARTIAL_SUFFIX) }
		}
	}

	@Test
	fun moveOnSameFileSystemRenames(){
		val source = file("a.txt", "content")
		val destination = output.resolve("sub/a.txt")

		operations().move(source, destination)

		assertThat(source).doesNotExist()
		assertThat(destination).hasContent("content")
		assertThat(atomicMoves).containsExactly(source to destination)
		assertThat(copies).isEmpty()
		assertNoPartial(destination.parent)
	}

	@Test
	fun moveAcrossFileSystemsCopiesSafely(){
		val source = file("a.txt", "content")
		val destination = output.resolve("a.txt")
		val operations = FileOperations(
			false,
			{ s, d -> throw AtomicMoveNotSupportedException(s.toString(), d.toString(), "test") },
			{ s, partial ->
				assertThat(s).describedAs("source kept while copying").exists()
				realCopy(s, partial)
			},
		)

		operations.move(source, destination)

		assertThat(copies).containsExactly(source to output.resolve(".a.txt${FileOperations.PARTIAL_SUFFIX}"))
		assertThat(source).doesNotExist()
		assertThat(destination).hasContent("content")
		assertNoPartial()
	}

	@Test
	fun truncatedCopyKeepsSource(){
		val source = file("a.txt", "some longer content")
		val destination = output.resolve("a.txt")

		assertThatThrownBy { operations(crossFileSystem = true, copy = ::truncatedCopy).move(source, destination) }
			.isInstanceOf(IOException::class.java)
			.hasMessageContaining("incomplete")

		assertThat(copies).hasSize(1)
		assertThat(source).hasContent("some longer content")
		assertThat(destination).doesNotExist()
		assertNoPartial()
	}

	@Test
	fun failingCopyKeepsSource(){
		val source = file("a.txt", "content")
		val destination = output.resolve("a.txt")

		assertThatThrownBy { operations(crossFileSystem = true, copy = ::failingCopy).move(source, destination) }
			.hasMessage("Network dropped")

		assertThat(source).hasContent("content")
		assertThat(destination).doesNotExist()
		assertNoPartial()
	}

	@Test
	fun failingCopyOperationKeepsSource(){
		val source = file("a.txt", "content")
		val destination = output.resolve("a.txt")

		assertThatThrownBy { operations(copy = ::failingCopy).moveWithCopy(source, destination) }.hasMessage("Network dropped")
		assertThatThrownBy { operations(copy = ::truncatedCopy).copy(source, destination) }.isInstanceOf(IOException::class.java)

		assertThat(source).hasContent("content")
		assertThat(destination).doesNotExist()
		assertNoPartial()
	}

	@Test
	fun copyKeepsSource(){
		val source = file("a.txt", "content")
		val time = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"))
		source.setLastModifiedTime(time)
		val destination = output.resolve("a.txt")

		operations().copy(source, destination)

		assertThat(source).hasContent("content")
		assertThat(destination).hasContent("content")
		assertThat(destination.getLastModifiedTime()).isEqualTo(time)
		assertThat(copies).hasSize(1)
		assertThat(atomicMoves).isEmpty()
		assertNoPartial()
	}

	@Test
	fun moveWithCopyDeletesSourceAfterCopy(){
		val source = file("a.txt", "content")
		val destination = output.resolve("a.txt")

		operations().moveWithCopy(source, destination)

		assertThat(source).doesNotExist()
		assertThat(destination).hasContent("content")
		assertThat(copies).hasSize(1)
		assertThat(atomicMoves).isEmpty()
		assertNoPartial()
	}

	@Test
	fun copyReplacesLeftoverPartial(){
		val source = file("a.txt", "content")
		val destination = output.resolve("a.txt")
		output.createDirectories().resolve(".a.txt${FileOperations.PARTIAL_SUFFIX}").writeText("old leftover, longer than content")

		operations().copy(source, destination)

		assertThat(destination).hasContent("content")
		assertNoPartial()
	}

	@Test
	fun neverOverwritesDestination(){
		val source = file("a.txt", "new")
		val destination = output.createDirectories().resolve("a.txt").also { it.writeText("old") }

		for(operation in listOf(Operation.COPY, Operation.MOVE, Operation.MOVE_WITH_COPY)){
			for(crossFileSystem in listOf(false, true)){
				assertThatThrownBy { operations(crossFileSystem).transfer(operation, source, destination) }
					.isInstanceOf(FileAlreadyExistsException::class.java)
			}
		}

		assertThat(source).hasContent("new")
		assertThat(destination).hasContent("old")
		assertThat(copies).isEmpty()
		assertNoPartial()
	}

	@Test
	fun destinationFolderExistingAsFile(){
		val source = file("a.txt")
		output.writeText("file")

		assertThatThrownBy { operations().move(source, output.resolve("a.txt")) }.isInstanceOf(IOException::class.java)

		assertThat(source).exists()
	}

	@Test
	fun dryRunDoesNothing(){
		val source = file("a.txt")
		val operations = FileOperations(true, { _, _ -> error("move") }, { _, _ -> error("copy") })

		operations.move(source, output.resolve("a.txt"))
		operations.copy(source, output.resolve("b.txt"))
		operations.moveWithCopy(source, output.resolve("c.txt"))

		assertThat(source).exists()
		assertThat(output).doesNotExist()
	}

	private fun converted(content: String = "converted"): Path = root.resolve("temp").createDirectories().resolve("123-a.mkv").also { it.writeText(content) }

	@Test
	fun commitConvertedAcrossFileSystems(){
		val source = file("a.mp4", "original")
		val time = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"))
		source.setLastModifiedTime(time)
		val converted = converted()
		val destination = output.resolve("a.mkv")

		operations(crossFileSystem = true).commitConverted(Operation.MOVE, source, converted, destination, false)

		assertThat(copies).containsExactly(converted to output.resolve(".a.mkv${FileOperations.PARTIAL_SUFFIX}"))
		assertThat(destination).hasContent("converted")
		assertThat(destination.getLastModifiedTime()).isEqualTo(time)
		assertThat(converted).doesNotExist()
		assertThat(source).doesNotExist()
		assertNoPartial()
	}

	@Test
	fun commitConvertedCopyKeepsSource(){
		val source = file("a.mp4", "original")
		val converted = converted()
		val destination = output.resolve("a.mkv")

		operations().commitConverted(Operation.COPY, source, converted, destination, false)

		assertThat(atomicMoves).containsExactly(converted to destination)
		assertThat(destination).hasContent("converted")
		assertThat(source).hasContent("original")
	}

	@Test
	fun commitConvertedFailureKeepsSource(){
		val source = file("a.mp4", "original")
		val converted = converted()
		val destination = output.resolve("a.mkv")

		assertThatThrownBy { operations(crossFileSystem = true, copy = ::truncatedCopy).commitConverted(Operation.MOVE, source, converted, destination, false) }
			.isInstanceOf(IOException::class.java)

		assertThat(source).hasContent("original")
		assertThat(converted).hasContent("converted")
		assertThat(destination).doesNotExist()
		assertNoPartial()
	}

	@Test
	fun commitConvertedReplacingSourceAcrossFileSystems(){
		val source = file("a.mkv", "original")
		val time = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"))
		source.setLastModifiedTime(time)
		val converted = converted()

		operations(crossFileSystem = true).commitConverted(Operation.MOVE, source, converted, source, true)

		assertThat(copies).hasSize(1)
		assertThat(source).hasContent("converted")
		assertThat(source.getLastModifiedTime()).isEqualTo(time)
		assertThat(converted).doesNotExist()
		assertThat(input.listDirectoryEntries().map { it.name }).containsExactly("a.mkv")
	}

	@Test
	fun commitConvertedReplacingSourceFailureRestoresSource(){
		val source = file("a.mkv", "original")
		val converted = converted()

		assertThatThrownBy { operations(crossFileSystem = true, copy = ::failingCopy).commitConverted(Operation.MOVE, source, converted, source, true) }
			.hasMessage("Network dropped")

		assertThat(source).hasContent("original")
		assertThat(converted).hasContent("converted")
		assertThat(input.listDirectoryEntries().map { it.name }).containsExactly("a.mkv")
	}

	@Test
	@OptIn(ExperimentalPathApi::class)
	fun realMoveAcrossFileSystems(){
		val shm = Path.of("/dev/shm")
		assumeTrue(Files.isDirectory(shm) && Files.isWritable(shm), "/dev/shm is not available")
		assumeTrue(Files.getFileStore(shm) != Files.getFileStore(root), "/dev/shm is on the same file store as $root")

		val target = Files.createTempDirectory(shm, "filesecure-test")
		try{
			val content = ByteArray(3 * 1024 * 1024) { it.toByte() }
			val source = input.resolve("big été.bin").also { it.writeBytes(content) }
			val destination = target.resolve("sub/big été.bin")

			FileOperations(false).move(source, destination)

			assertThat(source).doesNotExist()
			assertThat(destination).hasBinaryContent(content)
			assertNoPartial(destination.parent)
		}
		finally{
			target.deleteRecursively()
		}
	}
}
