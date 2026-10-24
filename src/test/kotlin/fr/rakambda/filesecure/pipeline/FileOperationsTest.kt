package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.Operation
import fr.rakambda.filesecure.config.Verification
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.IOException
import java.io.RandomAccessFile
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
import kotlin.io.path.readText
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
	private val written = mutableListOf<Pair<Path, Path>>()

	private fun file(name: String, content: String = name): Path = input.resolve(name).also { it.writeText(content) }

	private fun realCopy(source: Path, partial: Path){
		copies += source to partial
		Files.copy(source, partial, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
	}

	private fun operations(
		crossFileSystem: Boolean = false,
		copy: (Path, Path) -> Unit = ::realCopy,
		afterCopy: (Path, Path) -> Unit = { _, _ -> },
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
		{ source, partial ->
			written += source to partial
			afterCopy(source, partial)
		},
	)

	private val corrupt: (Path, Path) -> Unit = { _, partial -> flipMiddleByte(partial) }

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

	private fun binaryFile(name: String, size: Int): Pair<Path, ByteArray>{
		val content = ByteArray(size) { (it * 31 + it / 7).toByte() }
		return input.resolve(name).also { it.writeBytes(content) } to content
	}

	@Test
	fun checksumDetectsSameSizeCorruptionThatSizeMisses(){
		val source = file("a.txt", "some content")

		operations(crossFileSystem = true, afterCopy = corrupt).transfer(Operation.COPY, source, output.resolve("size.txt"), Verification.SIZE)
		assertThat(output.resolve("size.txt").readText()).describedAs("SIZE doesn't see the corruption").hasSize(12).isNotEqualTo("some content")

		assertThatThrownBy { operations(crossFileSystem = true, afterCopy = corrupt).transfer(Operation.MOVE, source, output.resolve("a.txt"), Verification.CHECKSUM) }
			.isInstanceOf(IOException::class.java)
			.hasMessageContaining("corrupted")
			.hasMessageContaining("SHA-256")
			.hasMessageContaining(source.toString())

		assertThat(source).hasContent("some content")
		assertThat(output.resolve("a.txt")).doesNotExist()
		assertNoPartial()
	}

	@ParameterizedTest
	@EnumSource(value = Operation::class, names = ["COPY", "MOVE", "MOVE_WITH_COPY"])
	fun checksumFailureKeepsSource(operation: Operation){
		val (source, content) = binaryFile("a.bin", 3 * 1024 * 1024 + 17)
		val time = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"))
		source.setLastModifiedTime(time)
		val destination = output.resolve("a.bin")

		assertThatThrownBy { operations(crossFileSystem = true, afterCopy = corrupt).transfer(operation, source, destination, Verification.CHECKSUM) }
			.isInstanceOf(IOException::class.java)
			.hasMessageContaining("corrupted")

		assertThat(written).hasSize(1)
		assertThat(copies).describedAs("own copy, not the SIZE one").isEmpty()
		assertThat(source).hasBinaryContent(content)
		assertThat(source.getLastModifiedTime()).isEqualTo(time)
		assertThat(destination).doesNotExist()
		assertNoPartial()
	}

	@ParameterizedTest
	@EnumSource(value = Operation::class, names = ["COPY", "MOVE", "MOVE_WITH_COPY"])
	fun checksumCopyKeepsContentAndTimes(operation: Operation){
		val (source, content) = binaryFile("a.bin", 3 * 1024 * 1024 + 17)
		val time = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"))
		source.setLastModifiedTime(time)
		val destination = output.resolve("sub/a.bin")

		operations(crossFileSystem = true).transfer(operation, source, destination, Verification.CHECKSUM)

		assertThat(written).containsExactly(source to output.resolve("sub/.a.bin${FileOperations.PARTIAL_SUFFIX}"))
		assertThat(copies).isEmpty()
		assertThat(destination).hasBinaryContent(content)
		assertThat(destination.getLastModifiedTime()).isEqualTo(time)
		if(operation.removesSource){
			assertThat(source).doesNotExist()
		}
		else{
			assertThat(source).hasBinaryContent(content)
		}
		assertNoPartial(destination.parent)
	}

	@Test
	fun checksumCopyReplacesLeftoverPartialAndHandlesEmptyFiles(){
		val source = file("a.txt", "")
		output.createDirectories().resolve(".a.txt${FileOperations.PARTIAL_SUFFIX}").writeText("old leftover")

		operations().copy(source, output.resolve("a.txt"), Verification.CHECKSUM)

		assertThat(output.resolve("a.txt")).isEmptyFile()
		assertNoPartial()
	}

	@Test
	fun checksumIsNotUsedForAtomicRenames(){
		val source = file("a.txt", "content")
		val destination = output.resolve("a.txt")

		operations(afterCopy = corrupt).move(source, destination, Verification.CHECKSUM)

		assertThat(atomicMoves).containsExactly(source to destination)
		assertThat(written).isEmpty()
		assertThat(destination).hasContent("content")
	}

	@Test
	fun checksumDryRunDoesNothing(){
		val source = file("a.txt")
		val operations = FileOperations(true, { _, _ -> error("move") }, { _, _ -> error("copy") }, { _, _ -> error("written") })

		for(operation in listOf(Operation.COPY, Operation.MOVE, Operation.MOVE_WITH_COPY)){
			operations.transfer(operation, source, output.resolve("a.txt"), Verification.CHECKSUM)
		}
		operations.commitConverted(Operation.MOVE, source, converted(), output.resolve("a.mkv"), false, Verification.CHECKSUM)

		assertThat(source).exists()
		assertThat(output).doesNotExist()
	}

	@Test
	fun commitConvertedWithChecksum(){
		val source = file("a.mp4", "original")
		val time = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"))
		source.setLastModifiedTime(time)
		val converted = converted()
		val destination = output.resolve("a.mkv")

		operations(crossFileSystem = true).commitConverted(Operation.MOVE, source, converted, destination, false, Verification.CHECKSUM)

		assertThat(written).containsExactly(converted to output.resolve(".a.mkv${FileOperations.PARTIAL_SUFFIX}"))
		assertThat(copies).isEmpty()
		assertThat(destination).hasContent("converted")
		assertThat(destination.getLastModifiedTime()).isEqualTo(time)
		assertThat(converted).doesNotExist()
		assertThat(source).doesNotExist()
		assertNoPartial()
	}

	@Test
	fun commitConvertedChecksumFailureKeepsSource(){
		val source = file("a.mp4", "original")
		val converted = converted()
		val destination = output.resolve("a.mkv")

		assertThatThrownBy { operations(crossFileSystem = true, afterCopy = corrupt).commitConverted(Operation.MOVE, source, converted, destination, false, Verification.CHECKSUM) }
			.isInstanceOf(IOException::class.java)
			.hasMessageContaining("corrupted")

		assertThat(source).hasContent("original")
		assertThat(converted).hasContent("converted")
		assertThat(destination).doesNotExist()
		assertNoPartial()
	}

	@Test
	fun commitConvertedReplacingSourceChecksumFailureRestoresSource(){
		val source = file("a.mkv", "original")
		val converted = converted()

		assertThatThrownBy { operations(crossFileSystem = true, afterCopy = corrupt).commitConverted(Operation.MOVE, source, converted, source, true, Verification.CHECKSUM) }
			.hasMessageContaining("corrupted")

		assertThat(source).hasContent("original")
		assertThat(converted).hasContent("converted")
		assertThat(input.listDirectoryEntries().map { it.name }).containsExactly("a.mkv")
	}

	@Test
	@OptIn(ExperimentalPathApi::class)
	fun realChecksumMoveAcrossFileSystems(){
		val shm = Path.of("/dev/shm")
		assumeTrue(Files.isDirectory(shm) && Files.isWritable(shm), "/dev/shm is not available")
		assumeTrue(Files.getFileStore(shm) != Files.getFileStore(root), "/dev/shm is on the same file store as $root")

		val target = Files.createTempDirectory(shm, "filesecure-test")
		try{
			val (source, content) = binaryFile("big été.bin", 5 * 1024 * 1024 + 3)
			val time = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"))
			source.setLastModifiedTime(time)
			val destination = target.resolve("sub/big été.bin")

			FileOperations(false).move(source, destination, Verification.CHECKSUM)

			assertThat(source).doesNotExist()
			assertThat(destination).hasBinaryContent(content)
			assertThat(destination.getLastModifiedTime()).isEqualTo(time)
			assertNoPartial(destination.parent)
		}
		finally{
			target.deleteRecursively()
		}
	}

	companion object{
		/**
		 * Flips one byte in the middle of the file, keeping its size.
		 */
		fun flipMiddleByte(path: Path){
			RandomAccessFile(path.toFile(), "rw").use { file ->
				val position = file.length() / 2
				file.seek(position)
				val byte = file.read()
				file.seek(position)
				file.write(byte xor 0xFF)
			}
		}
	}
}
