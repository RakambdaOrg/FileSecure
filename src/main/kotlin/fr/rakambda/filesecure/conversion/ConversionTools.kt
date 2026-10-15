package fr.rakambda.filesecure.conversion

import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.github.kokorin.jaffree.ffprobe.FFprobeResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * External programs used to probe and convert files.
 *
 * @param ffmpegDirectory Folder containing the ffmpeg executable, null to look it up in the PATH.
 * @param ffprobeDirectory Folder containing the ffprobe executable, null to look it up in the PATH.
 * @param magick ImageMagick executable.
 * @param ffmpegThreads Value of ffmpeg's `-threads` option, null to let ffmpeg decide.
 * @param processListener Called with every external process started for a conversion.
 */
class ConversionTools(
	private val ffmpegDirectory: Path? = null,
	private val ffprobeDirectory: Path? = null,
	val magick: String = "magick",
	val ffmpegThreads: Int? = null,
	val processListener: ((Process) -> Unit)? = null,
){
	private val probeLock = Semaphore(MAX_CONCURRENT_PROBES)

	fun ffmpeg(): CustomFFmpeg = CustomFFmpeg(ffmpegDirectory?.resolve("ffmpeg") ?: Path.of("ffmpeg"), processListener)

	/**
	 * @return The probe result, or null if the file couldn't be probed (not a media file for example).
	 */
	suspend fun probe(file: Path): FFprobeResult? = probeLock.withPermit {
		runInterruptible(Dispatchers.IO) {
			try{
				log.debug { "Probing file $file" }
				val ffprobe = ffprobeDirectory?.let { FFprobe.atPath(it) } ?: FFprobe.atPath()
				ffprobe.setShowStreams(true)
					.setShowFormat(true)
					.setInput(file.toString())
					.execute()
			}
			catch(e: Exception){
				log.warn { "Failed to probe file $file" }
				null
			}
		}
	}

	private companion object{
		const val MAX_CONCURRENT_PROBES = 10
	}
}
