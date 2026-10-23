package fr.rakambda.filesecure.stage

import fr.rakambda.filesecure.conversion.PlannedConversion
import fr.rakambda.filesecure.conversion.ProbeFilter
import fr.rakambda.filesecure.conversion.ProcessorType
import fr.rakambda.filesecure.pipeline.FileItem
import fr.rakambda.filesecure.pipeline.PipelineContext
import fr.rakambda.filesecure.storage.H2Storage
import fr.rakambda.filesecure.storage.NoOpStorage
import fr.rakambda.filesecure.storage.UselessFileStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.util.Locale

private val log = KotlinLogging.logger {}

/**
 * Plans the conversion of media files: the first processor able to handle the file is used, and the destination extension changes accordingly.
 * The conversion itself runs after all stages accepted the file, and only if its destination is free.
 */
data class Convert(
	/**
	 * Processors tried in order, empty to use the defaults.
	 */
	val processors: List<ProcessorType> = emptyList(),
	/**
	 * Extensions of the files to probe, empty to use the defaults.
	 */
	val extensions: Set<String> = emptySet(),
	val filters: List<ProbeFilter> = emptyList(),
	/**
	 * What happens to files that won't be converted.
	 */
	val unhandled: UnhandledStrategy = UnhandledStrategy.SKIP,
	/**
	 * Database remembering files that can't be converted, to avoid probing them on every run.
	 */
	val cache: Path? = null,
) : Stage{
	override fun open(context: PipelineContext): StageExecutor{
		val storage = cache?.let(::H2Storage) ?: NoOpStorage
		return ConvertExecutor(
			context,
			processors.ifEmpty { DEFAULT_PROCESSORS },
			extensions.map { it.lowercase(Locale.ROOT) }.toSet().ifEmpty { DEFAULT_EXTENSIONS },
			filters,
			unhandled,
			storage,
		)
	}

	companion object{
		val DEFAULT_PROCESSORS = listOf(ProcessorType.VIDEO_TO_HEVC_MKV, ProcessorType.MP3_TO_AAC, ProcessorType.TIFF_TO_JPG)
		val DEFAULT_EXTENSIONS = setOf("mp4", "mov", "mkv", "avi", "tiff", "mp3", "m4a", "m4v", "ts", "m2ts", "mts", "heic", "jpg", "raf")
	}
}

enum class UnhandledStrategy{
	/**
	 * The file continues through the pipeline unchanged.
	 */
	PASS,

	/**
	 * The file is left where it is.
	 */
	SKIP,
}

private class ConvertExecutor(
	private val context: PipelineContext,
	private val processors: List<ProcessorType>,
	private val extensions: Set<String>,
	private val filters: List<ProbeFilter>,
	private val unhandled: UnhandledStrategy,
	private val storage: UselessFileStorage,
) : StageExecutor{
	override suspend fun process(item: FileItem): StageResult{
		val file = item.source
		val lastModified = withContext(Dispatchers.IO) { Timestamp(Files.getLastModifiedTime(file).toMillis()) }
		if(storage.isUseless(file, lastModified)){
			return unhandled("known as not convertible")
		}
		if(file.extension !in extensions){
			storage.setUseless(file, lastModified)
			return unhandled("extension is not handled")
		}

		val probeResult = context.environment.tools.probe(file)
		val processor = processors.map { it.processor }.firstOrNull {
			try{
				it.canHandle(probeResult, file)
			}
			catch(e: Exception){
				log.warn(e) { "Failed to check if ${it.javaClass.simpleName} can handle $file, it is considered not handled" }
				false
			}
		}
		if(processor == null){
			storage.setUseless(file, lastModified)
			return unhandled("no processor can handle it")
		}
		if(!filters.all { it.test(probeResult) }){
			return unhandled("probe filters did not pass")
		}

		log.debug { "Processor ${processor.javaClass.simpleName} matched $file" }
		item.conversion = PlannedConversion(processor, probeResult)
		item.destination = item.destination.withFileName(item.destination.nameWithoutExtension + "." + processor.extension)
		return StageResult.Continue
	}

	private fun unhandled(reason: String): StageResult = when(unhandled){
		UnhandledStrategy.PASS -> StageResult.Continue
		UnhandledStrategy.SKIP -> StageResult.Skip("not converted, $reason")
	}

	override fun close() = storage.close()

	private val Path.extension: String
		get() = fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)

	private val Path.nameWithoutExtension: String
		get(){
			val name = fileName.toString()
			val dotIndex = name.lastIndexOf('.')
			return if(dotIndex <= 0) name else name.substring(0, dotIndex)
		}
}
