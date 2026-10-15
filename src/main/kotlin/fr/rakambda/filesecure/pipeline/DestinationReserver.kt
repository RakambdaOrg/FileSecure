package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.ConflictStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Picks destinations that are neither existing nor already given to another file being processed concurrently.
 */
class DestinationReserver{
	private val mutex = Mutex()
	private val reserved = HashSet<Path>()

	/**
	 * @return The destination to use, or null if the file should be skipped.
	 */
	suspend fun reserve(destination: Path, strategy: ConflictStrategy): Path? = mutex.withLock {
		withContext(Dispatchers.IO) {
			val candidate = when{
				isFree(destination) -> destination
				strategy == ConflictStrategy.SKIP -> null
				else -> generateSequence(1) { it + 1 }
					.map { destination.resolveSibling(numberedName(destination.fileName.toString(), it)) }
					.first(::isFree)
			}
			candidate?.also { reserved.add(it) }
		}
	}

	private fun isFree(path: Path) = path !in reserved && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)

	companion object{
		/**
		 * `name.ext` becomes `name (index).ext`, names without extension or starting with a dot get the suffix at their end.
		 */
		internal fun numberedName(name: String, index: Int): String{
			val dotIndex = name.lastIndexOf('.')
			if(dotIndex <= 0){
				return "$name ($index)"
			}
			return "${name.substring(0, dotIndex)} ($index)${name.substring(dotIndex)}"
		}
	}
}
