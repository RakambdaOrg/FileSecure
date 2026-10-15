package fr.rakambda.filesecure.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Timestamp

class H2StorageTest{
	@TempDir
	lateinit var root: Path

	@Test
	fun persistsUselessFiles(){
		val database = root.resolve("db/cache")
		val file = Path.of("/media/file.txt")

		H2Storage(database).use { it.setUseless(file, Timestamp(1000)) }

		H2Storage(database).use {
			assertThat(it.isUseless(file, Timestamp(1000))).isTrue()
			assertThat(it.isUseless(file, Timestamp(2000))).describedAs("modified since").isFalse()
			assertThat(it.isUseless(Path.of("/media/other.txt"), Timestamp(1000))).isFalse()
		}
	}
}
