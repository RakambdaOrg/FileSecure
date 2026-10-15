package fr.rakambda.filesecure.stage

import fr.rakambda.filesecure.metadata.FileMetadata
import fr.rakambda.filesecure.pipeline.FileItem
import java.nio.file.Path
import java.time.ZonedDateTime

/**
 * A file whose current destination is [destination] and whose date is [date].
 */
fun fileItem(
	outputBase: Path,
	destination: Path,
	source: Path = Path.of("/input/source.ext"),
	date: ZonedDateTime? = null,
): FileItem = FileItem(source, source.parent, outputBase) { FileMetadata(nameDate = date) }
	.also { it.destination = destination }
