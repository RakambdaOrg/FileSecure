package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.Operation
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

private val log = KotlinLogging.logger {}

/**
 * Every write to the file system goes through here, doing nothing during dry runs.
 */
class FileOperations(val dryRun: Boolean){
	fun transfer(operation: Operation, input: Path, output: Path){
		when(operation){
			Operation.COPY -> copy(input, output)
			Operation.MOVE -> move(input, output)
			Operation.MOVE_WITH_COPY -> moveWithCopy(input, output)
			Operation.NONE -> {}
		}
	}

	fun copy(input: Path, output: Path){
		if(dryRun){
			return
		}
		createDirectories(output.parent)
		Files.copy(input, output, StandardCopyOption.COPY_ATTRIBUTES)
	}

	fun move(input: Path, output: Path){
		if(dryRun){
			return
		}
		createDirectories(output.parent)
		Files.move(input, output)
	}

	fun moveWithCopy(input: Path, output: Path){
		if(dryRun){
			return
		}
		copy(input, output)
		Files.delete(input)
	}

	/**
	 * Puts a converted file at its destination, removing the source if the operation requires it.
	 *
	 * @param replaceSource true when the destination is the source itself, the source then being replaced by the converted file.
	 */
	fun commitConverted(operation: Operation, source: Path, converted: Path, destination: Path, replaceSource: Boolean){
		if(dryRun || operation == Operation.NONE){
			return
		}
		createDirectories(destination.parent)
		val sourceAttributes = Files.readAttributes(source, BasicFileAttributes::class.java)

		if(replaceSource){
			// Keep the original next to it until the converted file is in place, so it can be restored on failure
			val backup = source.resolveSibling("${source.fileName}.filesecure-original")
			Files.move(source, backup)
			try{
				Files.move(converted, destination)
			}
			catch(e: Exception){
				Files.move(backup, source)
				throw e
			}
			copyTimes(sourceAttributes, destination)
			trash(backup)
			return
		}

		Files.move(converted, destination)
		copyTimes(sourceAttributes, destination)
		if(operation.removesSource){
			trash(source)
		}
	}

	fun delete(path: Path){
		if(dryRun){
			return
		}
		Files.delete(path)
	}

	fun deleteIfExists(path: Path): Boolean{
		if(dryRun){
			return false
		}
		return Files.deleteIfExists(path)
	}

	/**
	 * Moves a file to the trash when the platform has one, deletes it otherwise.
	 */
	fun trash(path: Path){
		if(dryRun){
			return
		}
		if(moveToTrash(path)){
			log.debug { "Moved file $path to trash" }
			return
		}
		if(Files.deleteIfExists(path)){
			log.debug { "Deleted file $path" }
		}
	}

	fun setTimes(path: Path, lastModified: FileTime, creation: FileTime){
		if(dryRun){
			return
		}
		val view = Files.getFileAttributeView(path, BasicFileAttributeView::class.java)
		view.setTimes(lastModified, view.readAttributes().lastAccessTime(), creation)
	}

	private fun copyTimes(attributes: BasicFileAttributes, path: Path){
		Files.getFileAttributeView(path, BasicFileAttributeView::class.java)
			.setTimes(attributes.lastModifiedTime(), attributes.lastAccessTime(), attributes.creationTime())
	}

	private fun moveToTrash(path: Path): Boolean{
		try{
			if(GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()){
				return false
			}
			val desktop = Desktop.getDesktop()
			return desktop.isSupported(Desktop.Action.MOVE_TO_TRASH) && desktop.moveToTrash(path.toFile())
		}
		catch(e: Throwable){
			log.debug(e) { "Trash is not available" }
			return false
		}
	}

	private fun createDirectories(path: Path){
		Files.createDirectories(path)
		if(!Files.isDirectory(path)){
			throw IOException("Destination folder $path already exists as a file")
		}
	}
}
