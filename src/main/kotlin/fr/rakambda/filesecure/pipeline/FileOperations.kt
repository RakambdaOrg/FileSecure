package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.Operation
import fr.rakambda.filesecure.config.Verification
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.HexFormat

private val log = KotlinLogging.logger {}

/**
 * Every write to the file system goes through here, doing nothing during dry runs.
 */
class FileOperations internal constructor(
	val dryRun: Boolean,
	private val atomicMove: (Path, Path) -> Unit,
	private val copyToPartial: (Path, Path) -> Unit,
	/**
	 * Called with the source and the partial file once the partial file is written, before it is checked.
	 */
	private val afterCopy: (Path, Path) -> Unit = { _, _ -> },
){
	constructor(dryRun: Boolean) : this(dryRun, ::defaultAtomicMove, ::defaultCopyToPartial)

	fun transfer(operation: Operation, input: Path, output: Path, verification: Verification = Verification.SIZE){
		when(operation){
			Operation.COPY -> copy(input, output, verification)
			Operation.MOVE -> move(input, output, verification)
			Operation.MOVE_WITH_COPY -> moveWithCopy(input, output, verification)
			Operation.NONE -> {}
		}
	}

	fun copy(input: Path, output: Path, verification: Verification = Verification.SIZE){
		if(dryRun){
			return
		}
		createDirectories(output.parent)
		safeCopy(input, output, verification)
	}

	fun move(input: Path, output: Path, verification: Verification = Verification.SIZE){
		if(dryRun){
			return
		}
		createDirectories(output.parent)
		moveFile(input, output, verification)
	}

	fun moveWithCopy(input: Path, output: Path, verification: Verification = Verification.SIZE){
		if(dryRun){
			return
		}
		createDirectories(output.parent)
		safeCopy(input, output, verification)
		Files.delete(input)
	}

	/**
	 * Puts a converted file at its destination, removing the source if the operation requires it.
	 *
	 * @param replaceSource true when the destination is the source itself, the source then being replaced by the converted file.
	 */
	fun commitConverted(operation: Operation, source: Path, converted: Path, destination: Path, replaceSource: Boolean, verification: Verification = Verification.SIZE){
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
				moveFile(converted, destination, verification)
			}
			catch(e: Exception){
				try{
					Files.move(backup, source)
				}
				catch(restoreError: Exception){
					e.addSuppressed(restoreError)
				}
				throw e
			}
			copyTimes(sourceAttributes, destination)
			trash(backup)
			return
		}

		moveFile(converted, destination, verification)
		copyTimes(sourceAttributes, destination)
		if(operation.removesSource){
			trash(source)
		}
	}

	/**
	 * Renames the file when source and destination are on the same file system, otherwise copies it safely then deletes the source.
	 */
	private fun moveFile(source: Path, destination: Path, verification: Verification){
		ensureFree(destination)
		try{
			atomicMove(source, destination)
			return
		}
		catch(_: AtomicMoveNotSupportedException){
			log.debug { "$source and $destination are on different file systems, copying" }
		}
		safeCopy(source, destination, verification)
		Files.delete(source)
	}

	/**
	 * Copies to a partial file next to the destination, checks it then renames it, so the destination never holds an incomplete file.
	 * The source is never modified.
	 */
	private fun safeCopy(source: Path, destination: Path, verification: Verification){
		ensureFree(destination)
		val partial = partialFile(destination)
		try{
			val sourceAttributes = Files.readAttributes(source, BasicFileAttributes::class.java)
			val expectedSize = sourceAttributes.size()
			val sourceHash = when(verification){
				Verification.SIZE -> {
					copyToPartial(source, partial)
					null
				}
				Verification.CHECKSUM -> copyWithChecksum(source, partial)
			}
			afterCopy(source, partial)
			val size = Files.size(partial)
			if(size != expectedSize){
				throw IOException("Copy of $source to $partial is incomplete: $size bytes written out of $expectedSize")
			}
			if(sourceHash != null){
				val copyHash = checksum(partial)
				if(!sourceHash.contentEquals(copyHash)){
					throw IOException("Copy of $source to $partial is corrupted: SHA-256 ${shortHex(sourceHash)}... read from the source, ${shortHex(copyHash)}... read back from the copy")
				}
				log.debug { "Checksum of $partial matches $source" }
				copyTimesBestEffort(sourceAttributes, partial)
			}
			ensureFree(destination)
			try{
				Files.move(partial, destination, StandardCopyOption.ATOMIC_MOVE)
			}
			catch(_: AtomicMoveNotSupportedException){
				Files.move(partial, destination)
			}
		}
		catch(e: Throwable){
			try{
				Files.deleteIfExists(partial)
			}
			catch(deleteError: Exception){
				e.addSuppressed(deleteError)
			}
			throw e
		}
	}

	/**
	 * Copies the source to a new partial file, forced to storage.
	 *
	 * @return The SHA-256 of the bytes read from the source.
	 */
	private fun copyWithChecksum(source: Path, partial: Path): ByteArray{
		val digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM)
		Files.deleteIfExists(partial)
		FileChannel.open(source, StandardOpenOption.READ).use { input ->
			FileChannel.open(partial, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use { output ->
				val buffer = ByteBuffer.allocate(BUFFER_SIZE)
				while(input.read(buffer) >= 0){
					buffer.flip()
					digest.update(buffer.array(), 0, buffer.limit())
					while(buffer.hasRemaining()){
						output.write(buffer)
					}
					buffer.clear()
				}
				output.force(true)
			}
		}
		return digest.digest()
	}

	private fun checksum(path: Path): ByteArray{
		val digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM)
		FileChannel.open(path, StandardOpenOption.READ).use { input ->
			val buffer = ByteBuffer.allocate(BUFFER_SIZE)
			while(input.read(buffer) >= 0){
				buffer.flip()
				digest.update(buffer.array(), 0, buffer.limit())
				buffer.clear()
			}
		}
		return digest.digest()
	}

	/**
	 * Same as [copyTimes], ignoring the creation time where the file system can't set it.
	 */
	private fun copyTimesBestEffort(attributes: BasicFileAttributes, path: Path){
		try{
			copyTimes(attributes, path)
		}
		catch(e: IOException){
			log.debug { "Failed to set the creation time of $path, setting the other times only: ${e.message}" }
			Files.getFileAttributeView(path, BasicFileAttributeView::class.java).setTimes(attributes.lastModifiedTime(), attributes.lastAccessTime(), null)
		}
	}

	private fun ensureFree(destination: Path){
		if(Files.exists(destination, LinkOption.NOFOLLOW_LINKS)){
			throw FileAlreadyExistsException(destination.toString())
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

	companion object{
		const val PARTIAL_SUFFIX = ".filesecure-partial"
		private const val CHECKSUM_ALGORITHM = "SHA-256"
		private const val BUFFER_SIZE = 1024 * 1024

		fun partialFile(destination: Path): Path = destination.resolveSibling(".${destination.fileName}$PARTIAL_SUFFIX")

		private fun defaultAtomicMove(source: Path, destination: Path){
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
		}

		private fun defaultCopyToPartial(source: Path, partial: Path){
			Files.copy(source, partial, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
		}

		private fun shortHex(hash: ByteArray): String = HexFormat.of().formatHex(hash, 0, 8)
	}
}
