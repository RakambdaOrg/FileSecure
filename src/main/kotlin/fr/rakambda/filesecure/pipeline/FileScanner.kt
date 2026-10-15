package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.ScanOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

private val log = KotlinLogging.logger {}

/**
 * Walks an input folder, reporting the regular files and the folders that aren't excluded.
 */
class FileScanner(
	private val root: Path,
	private val options: ScanOptions,
	private val onFolder: (Path) -> Unit,
	private val onFile: (Path) -> Boolean,
) : FileVisitor<Path>{
	fun scan(){
		Files.walkFileTree(root, emptySet(), options.maxDepth, this)
	}

	override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult{
		if(options.skipHidden && dir != root && Files.isHidden(dir)){
			return FileVisitResult.SKIP_SUBTREE
		}
		val relative = root.relativize(dir).toString().replace("\\", "/")
		if(options.excludedFolders.any { it.matcher(relative).matches() }){
			return FileVisitResult.SKIP_SUBTREE
		}
		log.debug { "Entering folder $dir" }
		onFolder(dir)
		return FileVisitResult.CONTINUE
	}

	override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult{
		// Folders beyond the max depth are reported here, they must not be processed as files
		if(!attrs.isRegularFile){
			return FileVisitResult.CONTINUE
		}
		if(options.skipHidden && Files.isHidden(file)){
			return FileVisitResult.CONTINUE
		}
		return if(onFile(file)) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
	}

	override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult{
		log.warn { "Failed to visit $file: ${exc.message}" }
		return FileVisitResult.CONTINUE
	}

	override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult{
		exc?.let { log.warn { "Error while listing $dir: ${it.message}" } }
		return FileVisitResult.CONTINUE
	}
}
