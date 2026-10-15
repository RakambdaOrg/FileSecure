package fr.rakambda.filesecure.stage

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import fr.rakambda.filesecure.pipeline.FileItem
import fr.rakambda.filesecure.pipeline.PipelineContext
import java.nio.file.Path

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
	JsonSubTypes.Type(value = NameFilter::class, name = "NameFilter"),
	JsonSubTypes.Type(value = IfOlderThan::class, name = "IfOlderThan"),
	JsonSubTypes.Type(value = FolderCanonical::class, name = "FolderCanonical"),
	JsonSubTypes.Type(value = FolderPerDay::class, name = "FolderPerDay"),
	JsonSubTypes.Type(value = FolderPerWeek::class, name = "FolderPerWeek"),
	JsonSubTypes.Type(value = FolderPerMonth::class, name = "FolderPerMonth"),
	JsonSubTypes.Type(value = FolderPerYear::class, name = "FolderPerYear"),
	JsonSubTypes.Type(value = FolderUniquePerDay::class, name = "FolderUniquePerDay"),
	JsonSubTypes.Type(value = Lowercase::class, name = "Lowercase"),
	JsonSubTypes.Type(value = NoRename::class, name = "NoRename"),
	JsonSubTypes.Type(value = RenameWithDate::class, name = "RenameWithDate"),
	JsonSubTypes.Type(value = RenameWithDatePattern::class, name = "RenameWithDatePattern"),
	JsonSubTypes.Type(value = Convert::class, name = "Convert"),
)
sealed interface Stage{
	/**
	 * Creates what runs this stage during one execution of a pipeline.
	 */
	fun open(context: PipelineContext): StageExecutor
}

fun interface StageExecutor : AutoCloseable{
	suspend fun process(item: FileItem): StageResult

	override fun close(){
	}
}

sealed interface StageResult{
	data object Continue : StageResult

	data class Skip(val reason: String) : StageResult
}

/**
 * A stage without any state or resource.
 */
sealed interface SimpleStage : Stage{
	fun process(item: FileItem): StageResult

	override fun open(context: PipelineContext): StageExecutor = StageExecutor { process(it) }
}

/**
 * A stage changing the destination of the file.
 */
sealed interface PathStage : SimpleStage{
	/**
	 * @return The new destination, or null to keep the current one.
	 */
	fun transform(item: FileItem): Path?

	override fun process(item: FileItem): StageResult{
		transform(item)?.let { item.destination = it }
		return StageResult.Continue
	}
}

/**
 * The destination path relative to the output folder.
 */
internal val FileItem.relativeDestination: Path
	get() = outputBase.relativize(destination)

internal fun Path.withFileName(name: String): Path = parent?.resolve(name) ?: fileSystem.getPath(name)

internal val Path.extensionWithDot: String
	get(){
		val name = fileName.toString()
		val dotIndex = name.lastIndexOf('.')
		return if(dotIndex <= 0) "" else name.substring(dotIndex)
	}
