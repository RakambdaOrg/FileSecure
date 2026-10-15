package fr.rakambda.filesecure.folder

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import fr.rakambda.filesecure.pipeline.FileOperations
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId

private val log = KotlinLogging.logger {}

/**
 * An action applied to every scanned input folder once all files have been processed, deepest folders first.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
	JsonSubTypes.Type(value = DeleteIfEmpty::class, name = "DeleteIfEmpty"),
	JsonSubTypes.Type(value = DeleteIfOlderThan::class, name = "DeleteIfOlderThan"),
	JsonSubTypes.Type(value = DeleteChildren::class, name = "DeleteChildren"),
)
sealed interface FolderAction{
	/**
	 * @param folder The folder to apply the action to.
	 * @param baseFolder The input folder of the mapping.
	 */
	fun apply(fileOperations: FileOperations, folder: Path, baseFolder: Path)
}

/**
 * Depth of the folder below the input folder, the input folder itself being at depth 0.
 */
internal fun depthOf(folder: Path, baseFolder: Path): Int{
	if(folder == baseFolder){
		return 0
	}
	return baseFolder.relativize(folder).nameCount
}

internal fun isEmptyFolder(folder: Path): Boolean = Files.list(folder).use { it.findAny().isEmpty }

/**
 * Deletes empty folders at least [depth] levels below the input folder.
 */
data class DeleteIfEmpty(
	val depth: Int = 1,
) : FolderAction{
	override fun apply(fileOperations: FileOperations, folder: Path, baseFolder: Path){
		try{
			if(depthOf(folder, baseFolder) < depth || !isEmptyFolder(folder)){
				return
			}
			log.info { "Deleting folder $folder because it is empty" }
			fileOperations.delete(folder)
		}
		catch(e: Exception){
			log.error(e) { "Failed to determine if $folder should be deleted, it will not be by default" }
		}
	}
}

/**
 * Deletes empty folders at least [depth] levels below the input folder, whose last modification is older than the given offset.
 */
data class DeleteIfOlderThan(
	val dayOffset: Long = 0,
	val minuteOffset: Long = 0,
	val depth: Int = 1,
) : FolderAction{
	override fun apply(fileOperations: FileOperations, folder: Path, baseFolder: Path){
		try{
			if(depthOf(folder, baseFolder) < depth){
				return
			}
			val modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(folder).toInstant(), ZoneId.systemDefault())
			if(!modified.isBefore(LocalDateTime.now().minusDays(dayOffset).minusMinutes(minuteOffset))){
				return
			}
			if(isEmptyFolder(folder)){
				log.info { "Deleting folder $folder because it is more than $dayOffset days old and empty" }
				fileOperations.delete(folder)
			}
			else{
				log.debug { "Tried deleting folder $folder because it is more than $dayOffset days old but wasn't empty" }
			}
		}
		catch(e: Exception){
			log.error(e) { "Failed to determine if $folder should be deleted, it will not be by default" }
		}
	}
}

/**
 * Deletes the given children of every folder, if they exist.
 */
data class DeleteChildren(
	val names: List<String> = emptyList(),
) : FolderAction{
	override fun apply(fileOperations: FileOperations, folder: Path, baseFolder: Path){
		for(name in names){
			val child = folder.resolve(name)
			try{
				if(fileOperations.deleteIfExists(child)){
					log.info { "Deleted child file $child" }
				}
			}
			catch(e: Exception){
				log.error(e) { "Failed to delete child $child" }
			}
		}
	}
}
