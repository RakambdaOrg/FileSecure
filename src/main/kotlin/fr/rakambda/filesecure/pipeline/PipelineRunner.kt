package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.Mapping
import fr.rakambda.filesecure.config.Operation
import fr.rakambda.filesecure.config.Pipeline
import fr.rakambda.filesecure.conversion.ConversionException
import fr.rakambda.filesecure.conversion.PlannedConversion
import fr.rakambda.filesecure.folder.FolderAction
import fr.rakambda.filesecure.stage.Convert
import fr.rakambda.filesecure.stage.StageExecutor
import fr.rakambda.filesecure.stage.StageResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentLinkedQueue

private val log = KotlinLogging.logger {}

class PipelineRunner(private val environment: RunEnvironment){
	/**
	 * @param stats Updated while the pipeline runs, so counts are kept if it fails or is cancelled.
	 */
	suspend fun run(pipeline: Pipeline, name: String, stats: PipelineStats = PipelineStats()): PipelineStats{
		log.info { "Running pipeline $name" }
		val context = PipelineContext(environment, pipeline)
		val executors = mutableListOf<StageExecutor>()
		try{
			withContext(Dispatchers.IO) {
				pipeline.stages.forEach { executors += it.open(context) }
			}
			val tempDirectory = if(pipeline.stages.any { it is Convert } && !environment.dryRun) createTempDirectory(pipeline.temp) else null
			try{
				for(mapping in pipeline.mappings){
					MappingRun(pipeline, mapping, executors, tempDirectory, stats).run()
				}
			}
			finally{
				tempDirectory?.let { withContext(NonCancellable) { deleteTempDirectory(it) } }
			}
		}
		finally{
			executors.forEach { executor ->
				try{
					executor.close()
				}
				catch(e: Exception){
					log.error(e) { "Failed to close stage $executor" }
					stats.error()
				}
			}
		}
		return stats
	}

	private suspend fun createTempDirectory(parent: Path?): Path = withContext(Dispatchers.IO) {
		if(parent == null){
			Files.createTempDirectory(TEMP_PREFIX)
		}
		else{
			Files.createDirectories(parent)
			Files.createTempDirectory(parent, TEMP_PREFIX)
		}
	}

	private suspend fun deleteTempDirectory(directory: Path) = withContext(Dispatchers.IO) {
		try{
			Files.delete(directory)
		}
		catch(e: Exception){
			log.warn { "Failed to delete temporary directory $directory, it may still contain files: ${e.message}" }
		}
	}

	private inner class MappingRun(
		private val pipeline: Pipeline,
		private val mapping: Mapping,
		private val executors: List<StageExecutor>,
		private val tempDirectory: Path?,
		private val stats: PipelineStats,
	){
		private val reserver = DestinationReserver()
		private val fileOperations = environment.fileOperations
		private val progress = environment.progress

		suspend fun run(){
			if(!withContext(Dispatchers.IO) { Files.isDirectory(mapping.input) }){
				log.warn { "Input folder ${mapping.input} doesn't exist, skipping mapping" }
				return
			}
			log.info { "Processing mapping (${pipeline.operation}) ${mapping.input} ==> ${mapping.output}" }

			val folders = ConcurrentLinkedQueue<Path>()
			coroutineScope {
				val files = Channel<Path>(FILE_BUFFER_SIZE)
				launch {
					try{
						runInterruptible(Dispatchers.IO) {
							FileScanner(mapping.input, pipeline.scan, folders::add) { file ->
								progress.fileFound()
								stats.fileFound()
								files.trySendBlocking(file).isSuccess
							}.scan()
						}
					}
					finally{
						files.close()
					}
				}
				repeat(environment.workers) {
					launch(Dispatchers.IO) {
						for(file in files){
							processFile(file)
						}
					}
				}
			}

			if(pipeline.folderActions.isNotEmpty()){
				log.debug { "Applying folder actions" }
				withContext(Dispatchers.IO) {
					folders.sortedByDescending { it.nameCount }.forEach { folder ->
						pipeline.folderActions.forEach { action -> applyFolderAction(action, folder) }
					}
				}
			}
		}

		private fun applyFolderAction(action: FolderAction, folder: Path){
			try{
				action.apply(fileOperations, folder, mapping.input)
			}
			catch(e: Exception){
				log.error(e) { e.message ?: "Failed to apply $action to $folder" }
				stats.error()
			}
		}

		private suspend fun processFile(file: Path){
			val item = FileItem(file, mapping.input, mapping.output) { environment.metadataExtractor.getMetadata(file) }
			try{
				for(executor in executors){
					environment.pauseGate.awaitResumed()
					val result = executor.process(item)
					if(result is StageResult.Skip){
						log.debug { "Skipping $file: ${result.reason}" }
						stats.record(FileOutcome.SKIPPED_BY_STAGE)
						return
					}
				}
				commit(item)
			}
			catch(e: CancellationException){
				throw e
			}
			catch(e: Exception){
				log.error(e) { "Failed to process $file" }
				// Failures caused by a cancellation aren't errors
				if(currentCoroutineContext().isActive){
					stats.error()
				}
			}
			finally{
				progress.fileProcessed()
			}
		}

		private suspend fun commit(item: FileItem){
			val source = item.source
			val conversion = item.conversion
			val operation = pipeline.operation

			val isSource = item.destination.toAbsolutePath().normalize() == source.toAbsolutePath().normalize()
			if(isSource && conversion == null){
				log.debug { "$source is already at its destination" }
				stats.record(FileOutcome.ALREADY_IN_PLACE)
				return
			}
			// The source is replaced by its converted version, there's no conflict as the source will be gone
			val replaceSource = isSource && operation.removesSource
			val destination = if(replaceSource) item.destination else reserver.reserve(item.destination, pipeline.conflict)
			if(destination == null){
				log.info { "Skipping $source, destination ${item.destination} already exists" }
				stats.record(FileOutcome.DESTINATION_EXISTS)
				return
			}

			val conversionDescription = conversion?.let { " converted to ${it.processor.extension}" } ?: ""
			log.info { "$operation file $source to $destination$conversionDescription" }
			if(operation == Operation.NONE){
				stats.record(FileOutcome.PROCESSED)
				return
			}

			// Read before the source may disappear
			val fileDate = if(pipeline.setFileCreationDate) item.metadata.nameDate ?: item.metadata.takenDate else null

			if(conversion == null){
				withContext(Dispatchers.IO) { fileOperations.transfer(operation, source, destination) }
				stats.record(if(operation == Operation.COPY) FileOutcome.COPIED else FileOutcome.MOVED)
			}
			else{
				val converted = convert(item, conversion, destination)
				if(converted == null){
					stats.record(FileOutcome.CONVERTED)
					return
				}
				try{
					withContext(Dispatchers.IO) { fileOperations.commitConverted(operation, source, converted, destination, replaceSource) }
				}
				finally{
					withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(converted) }
				}
				stats.record(FileOutcome.CONVERTED)
			}

			fileDate?.let {
				log.info { "Setting creation date for $destination to ${it.toInstant()}" }
				try{
					val time = FileTime.from(it.toInstant())
					withContext(Dispatchers.IO) { fileOperations.setTimes(destination, time, time) }
				}
				catch(e: CancellationException){
					throw e
				}
				catch(e: Exception){
					log.warn(e) { "Failed to set file creation date to obtained metadata" }
					stats.error()
				}
			}
		}

		/**
		 * @return The converted file in the temporary directory, null during dry runs.
		 */
		private suspend fun convert(item: FileItem, conversion: PlannedConversion, destination: Path): Path?{
			// No temporary directory is created during dry runs, the path is only used in logs
			val directory = tempDirectory ?: pipeline.temp ?: Path.of(System.getProperty("java.io.tmpdir"))
			val converted = directory.resolve("${System.nanoTime()}-${item.source.hashCode()}-${destination.fileName}")
			val converter = conversion.processor.createConverter(environment.tools, conversion.probeResult, item.source, converted)
			if(environment.dryRun){
				log.info { "Dry run: would have executed `${converter.description}`" }
				return null
			}

			progress.conversionQueued()
			try{
				environment.conversionLock.withPermit {
					environment.pauseGate.awaitResumed()
					log.info { "Converting ${item.source} to $converted" }
					progress.task(destination.fileName.toString()).use { converter.convert(it) }
				}
				val size = withContext(Dispatchers.IO) { if(Files.exists(converted)) Files.size(converted) else -1 }
				if(size <= 0){
					throw ConversionException("Converted file $converted is missing or empty")
				}
				log.debug { "Converted ${item.source} to $converted" }
				return converted
			}
			catch(e: Throwable){
				withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(converted) }
				throw e
			}
			finally{
				progress.conversionDone()
			}
		}
	}

	private companion object{
		const val TEMP_PREFIX = "FileSecure"
		const val FILE_BUFFER_SIZE = 1000
	}
}
