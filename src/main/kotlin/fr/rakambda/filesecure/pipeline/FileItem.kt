package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.conversion.PlannedConversion
import fr.rakambda.filesecure.metadata.FileMetadata
import java.nio.file.Path

/**
 * A file going through the stages of a pipeline.
 *
 * Stages only plan what will happen to the file (destination, conversion), the plan is executed once all stages accepted the file.
 */
class FileItem(
	val source: Path,
	val inputBase: Path,
	val outputBase: Path,
	metadataProvider: () -> FileMetadata,
){
	/**
	 * Same relative path in the output as in the input.
	 */
	val originalDestination: Path = outputBase.resolve(inputBase.relativize(source))
	var destination: Path = originalDestination
	var conversion: PlannedConversion? = null

	/**
	 * Computed on first access as reading media metadata can be costly.
	 */
	val metadata: FileMetadata by lazy(metadataProvider)

	override fun toString(): String = source.toString()
}
