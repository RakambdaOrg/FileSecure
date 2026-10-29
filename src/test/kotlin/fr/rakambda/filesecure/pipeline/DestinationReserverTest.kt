package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.ConflictStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DestinationReserverTest{
	@TempDir
	lateinit var root: Path

	@Test
	fun numberedName(){
		assertThat(DestinationReserver.numberedName("file.ext", 2)).isEqualTo("file (2).ext")
		assertThat(DestinationReserver.numberedName("archive.tar.gz", 1)).isEqualTo("archive.tar (1).gz")
		assertThat(DestinationReserver.numberedName("Makefile", 1)).isEqualTo("Makefile (1)")
		assertThat(DestinationReserver.numberedName(".bashrc", 1)).isEqualTo(".bashrc (1)")
	}

	@Test
	fun keepsFreeDestination(): Unit = runBlocking {
		val destination = root.resolve("file.txt")

		assertThat(DestinationReserver().reserve(destination, ConflictStrategy.SKIP)).isEqualTo(destination)
	}

	@Test
	fun renamesExistingDestination(): Unit = runBlocking {
		val destination = Files.createFile(root.resolve("file.txt"))
		Files.createFile(root.resolve("file (1).txt"))

		assertThat(DestinationReserver().reserve(destination, ConflictStrategy.RENAME)).isEqualTo(root.resolve("file (2).txt"))
	}

	@Test
	fun skipsExistingDestination(): Unit = runBlocking {
		val destination = Files.createFile(root.resolve("file.txt"))

		assertThat(DestinationReserver().reserve(destination, ConflictStrategy.SKIP)).isNull()
	}

	@Test
	fun concurrentReservationsGetDistinctNames(): Unit = runBlocking {
		val reserver = DestinationReserver()
		val destination = root.resolve("file.txt")

		val reserved = (1..50).map { async(Dispatchers.Default) { reserver.reserve(destination, ConflictStrategy.RENAME) } }.awaitAll()

		assertThat(reserved).doesNotHaveDuplicates().hasSize(50)
	}
}
