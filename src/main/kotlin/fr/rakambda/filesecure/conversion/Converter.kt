package fr.rakambda.filesecure.conversion

import com.github.kokorin.jaffree.ffmpeg.FFmpegProgress
import com.github.kokorin.jaffree.ffmpeg.ProgressListener
import com.github.kokorin.jaffree.ffmpeg.UrlInput
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import com.github.kokorin.jaffree.ffprobe.FFprobeResult
import fr.rakambda.filesecure.progress.TaskProgress
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Duration

private val log = KotlinLogging.logger {}

/**
 * Converts one file into another.
 */
interface Converter{
	/**
	 * Human-readable command, used for dry runs.
	 */
	val description: String

	/**
	 * Runs the conversion, the external process is killed if the coroutine is cancelled.
	 *
	 * @throws ConversionException If the conversion failed.
	 */
	suspend fun convert(progress: TaskProgress)
}

class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

class FfmpegConverter(
	private val tools: ConversionTools,
	private val probeResult: FFprobeResult?,
	private val input: Path,
	private val output: Path,
	private val configureOutput: (UrlOutput) -> UrlOutput,
) : Converter{
	override val description: String
		get() = build(null).commandLine

	override suspend fun convert(progress: TaskProgress){
		val duration = probeResult?.format?.duration?.let { Duration.ofSeconds(it.toLong()) } ?: Duration.ZERO
		log.debug { "Converting $input ($duration) to $output" }
		progress.setMax(duration.toMillis())

		val future = build { ffmpegProgress -> ffmpegProgress.timeMillis?.takeIf { it >= 0 }?.let(progress::stepTo) }
			.executeAsync()
		try{
			future.toCompletableFuture().await()
		}
		catch(e: CancellationException){
			future.forceStop()
			throw e
		}
		catch(e: Exception){
			throw ConversionException("ffmpeg failed to convert $input: ${describeFailure(e)}", e)
		}
	}

	private fun build(progressListener: ProgressListener?): CustomFFmpeg{
		val output = configureOutput(UrlOutput.toPath(output))
		tools.ffmpegThreads?.takeIf { it >= 0 }?.let { output.addArguments("-threads", it.toString()) }

		val ffmpeg = tools.ffmpeg()
		ffmpeg.addInput(UrlInput.fromPath(input))
			.addOutput(output)
			.setOverwriteOutput(false)
		progressListener?.let(ffmpeg::setProgressListener)
		return ffmpeg
	}
}

/**
 * Runs an external command expected to write the output file.
 */
class CommandConverter(
	private val tools: ConversionTools,
	private val command: List<String>,
) : Converter{
	override val description: String
		get() = command.joinToString(" ") { "\"$it\"" }

	override suspend fun convert(progress: TaskProgress){
		progress.setMax(1)
		val process = runInterruptible(Dispatchers.IO) {
			ProcessBuilder(command)
				.redirectErrorStream(true)
				.start()
		}
		try{
			tools.processListener?.invoke(process)
			coroutineScope {
				val output = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText() } }
				val exitCode = try{
					process.onExit().await().exitValue()
				}
				catch(e: CancellationException){
					// Killing the process ends its output stream, letting the reader above complete
					process.destroyForcibly()
					throw e
				}
				if(exitCode != 0){
					throw ConversionException("Command `$description` exited with code $exitCode: ${output.await().trim()}")
				}
			}
			progress.stepTo(1)
		}
		finally{
			if(process.isAlive){
				withContext(NonCancellable + Dispatchers.IO) { process.destroyForcibly().waitFor() }
			}
		}
	}
}
