package fr.rakambda.filesecure.config

import fr.rakambda.filesecure.folder.FolderAction
import fr.rakambda.filesecure.stage.Stage
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * A sequence of stages applied to every file found in the inputs of its mappings.
 */
data class Pipeline(
	val name: String? = null,
	val enabled: Boolean = true,
	val mappings: List<Mapping> = emptyList(),
	val scan: ScanOptions = ScanOptions(),
	val stages: List<Stage> = emptyList(),
	val operation: Operation = Operation.NONE,
	val conflict: ConflictStrategy = ConflictStrategy.RENAME,
	val verify: Verification = Verification.SIZE,
	val setFileCreationDate: Boolean = false,
	val temp: Path? = null,
	val folderActions: List<FolderAction> = emptyList(),
){
	fun displayName(index: Int): String = name ?: "#${index + 1}"
}

data class Mapping(
	val input: Path,
	val output: Path,
)

data class ScanOptions(
	val maxDepth: Int = Int.MAX_VALUE,
	val skipHidden: Boolean = false,
	/**
	 * Regexes matched against the folder path relative to the input, using `/` as separator.
	 */
	val excludedFolders: List<Pattern> = emptyList(),
)

enum class Operation(val removesSource: Boolean){
	COPY(false),
	MOVE(true),
	MOVE_WITH_COPY(true),
	NONE(false),
}

enum class ConflictStrategy{
	/**
	 * Use the first free name among `name (1).ext`, `name (2).ext`...
	 */
	RENAME,
	SKIP,
}

/**
 * How a file copied to its destination is checked before being renamed to its final name.
 */
enum class Verification{
	SIZE,
	/**
	 * The SHA-256 of the source, computed while copying, is compared with the one of the copy read back.
	 */
	CHECKSUM,
}
