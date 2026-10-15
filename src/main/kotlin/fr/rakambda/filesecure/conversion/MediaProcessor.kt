package fr.rakambda.filesecure.conversion

import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import com.github.kokorin.jaffree.ffprobe.FFprobeResult
import com.github.kokorin.jaffree.ffprobe.Stream
import java.nio.file.Path
import java.util.Locale

/**
 * Decides whether a file should be converted and how.
 */
sealed interface MediaProcessor{
	/**
	 * Extension of the converted file, without the dot.
	 */
	val extension: String

	fun canHandle(probeResult: FFprobeResult?, file: Path): Boolean

	fun createConverter(tools: ConversionTools, probeResult: FFprobeResult?, input: Path, output: Path): Converter
}

/**
 * A conversion decided by a Convert stage, executed once all stages accepted the file.
 */
data class PlannedConversion(
	val processor: MediaProcessor,
	val probeResult: FFprobeResult?,
)

enum class ProcessorType(val processor: MediaProcessor){
	VIDEO_TO_HEVC_MP4(VideoToHevcProcessor("mp4", "mov,mp4,m4a,3gp,3g2,mj2")),
	VIDEO_TO_HEVC_MKV(VideoToHevcProcessor("mkv", "matroska,webm")),
	VIDEO_TO_AV1_MKV(VideoToAv1Processor),
	MP3_TO_AAC(SingleAudioStreamProcessor("m4a", setOf("mp3")) { it.setCodec(StreamType.AUDIO, "aac").addArguments("-b:a", "192k") }),
	AUDIO_TO_OPUS(SingleAudioStreamProcessor("opus", setOf("mp3", "aac")) { it.setCodec(StreamType.AUDIO, "libopus").addArguments("-b:a", "128000") }),
	TIFF_TO_JPG(TiffToJpgProcessor),
	PHOTO_TO_AVIF(PhotoProcessor("avif")),
	PHOTO_TO_JXL(PhotoProcessor("jxl")),
}

class VideoToHevcProcessor(
	override val extension: String,
	/**
	 * ffprobe's format name of the target container.
	 */
	private val formatName: String,
) : MediaProcessor{
	override fun canHandle(probeResult: FFprobeResult?, file: Path): Boolean{
		if(probeResult == null || isPicture(probeResult)){
			return false
		}
		return isOtherVideoCodec(probeResult) || isHevcWithOtherTag(probeResult) || isHevcWithOtherContainer(probeResult)
	}

	override fun createConverter(tools: ConversionTools, probeResult: FFprobeResult?, input: Path, output: Path): Converter{
		// Already HEVC: only the container or codec tag needs to change, no re-encoding
		val remuxOnly = probeResult != null && isHevcWithOtherContainer(probeResult)
		return FfmpegConverter(tools, probeResult, input, output) {
			if(remuxOnly){
				it.copyAllCodecs()
					.addArguments("-tag:v", "hvc1")
			}
			else{
				it.setCodec(StreamType.SUBTITLE, "copy")
					.setCodec(StreamType.AUDIO, "aac")
					.addArguments("-b:a", "128000")
					.setCodec(StreamType.VIDEO, "libx265")
					.addArguments("-preset", "medium")
					.addArguments("-crf", "21")
					.addArguments("-vf", "format=yuv420p10le")
					.addArguments("-movflags", "+faststart+use_metadata_tags")
					.addArguments("-map_metadata", "0")
					.addArguments("-map", "0")
					.addArguments("-map", "-0:d")
					.addArguments("-max_muxing_queue_size", "512")
					.addArguments("-tag:v", "hvc1")
			}
		}
	}

	private fun isPicture(probeResult: FFprobeResult) = probeResult.streams.any { it.profile == "Main Still Picture" }

	private fun isOtherVideoCodec(probeResult: FFprobeResult) = probeResult.streams.any { it.codecName in CODECS }

	private fun isHevcWithOtherTag(probeResult: FFprobeResult) = probeResult.streams.any { isHevc(it) && !isTargetCodecTag(it) }

	private fun isHevcWithOtherContainer(probeResult: FFprobeResult): Boolean{
		val isTargetContainer = probeResult.format?.formatName == formatName
		val isTargetTag = probeResult.streams.any(::isTargetCodecTag)
		val isHevc = probeResult.streams.any(::isHevc)
		return isHevc && (!isTargetContainer || !isTargetTag)
	}

	private fun isHevc(stream: Stream) = stream.codecName == "hevc"

	private fun isTargetCodecTag(stream: Stream) = stream.codecTagString == "hvc1" || stream.codecTagString == "[0][0][0][0]"

	private companion object{
		val CODECS = setOf("h264", "vp9", "wmv3", "mpeg2video")
	}
}

object VideoToAv1Processor : MediaProcessor{
	private val CODECS = setOf("h264", "vp9", "wmv3", "mpeg2video", "hevc", "h265")

	override val extension = "mkv"

	override fun canHandle(probeResult: FFprobeResult?, file: Path): Boolean = probeResult?.streams.orEmpty().any { it.codecName in CODECS }

	override fun createConverter(tools: ConversionTools, probeResult: FFprobeResult?, input: Path, output: Path): Converter =
		FfmpegConverter(tools, probeResult, input, output) {
			it.setCodec(StreamType.SUBTITLE, "copy")
				.setCodec(StreamType.AUDIO, "libopus")
				.addArguments("-b:a", "128000")
				.setCodec(StreamType.VIDEO, "libsvtav1")
				.addArguments("-preset", "4")
				.addArguments("-crf", "32")
				.addArguments("-g", "240")
				.addArguments("-svtav1-params", "tune=0:enable-overlays=1:scd=1:scm=2")
				.addArguments("-pix_fmt", "yuv420p10le")
				.addArguments("-movflags", "+faststart+use_metadata_tags")
				.addArguments("-map_metadata", "0")
				.addArguments("-map", "0")
				.addArguments("-map", "-0:d")
				.addArguments("-max_muxing_queue_size", "512")
		}
}

/**
 * Converts files made of a single audio stream.
 */
class SingleAudioStreamProcessor(
	override val extension: String,
	private val codecs: Set<String>,
	private val configureOutput: (UrlOutput) -> UrlOutput,
) : MediaProcessor{
	override fun canHandle(probeResult: FFprobeResult?, file: Path): Boolean{
		val streams = probeResult?.streams ?: return false
		return streams.size == 1 && streams.all { it.codecName in codecs }
	}

	override fun createConverter(tools: ConversionTools, probeResult: FFprobeResult?, input: Path, output: Path): Converter =
		FfmpegConverter(tools, probeResult, input, output, configureOutput)
}

object TiffToJpgProcessor : MediaProcessor{
	override val extension = "jpg"

	override fun canHandle(probeResult: FFprobeResult?, file: Path): Boolean = probeResult?.streams.orEmpty().any { it.codecName == "tiff" }

	override fun createConverter(tools: ConversionTools, probeResult: FFprobeResult?, input: Path, output: Path): Converter =
		CommandConverter(tools, listOf(
			tools.magick,
			"-quality", "85%",
			"-sampling-factor", "4:2:0",
			"-interlace", "JPEG",
			"-colorspace", "sRGB",
			input.toAbsolutePath().toString(),
			output.toAbsolutePath().toString(),
		))
}

/**
 * Converts photos with ImageMagick, the output format being deduced from the extension.
 */
class PhotoProcessor(override val extension: String) : MediaProcessor{
	override fun canHandle(probeResult: FFprobeResult?, file: Path): Boolean{
		val name = file.fileName.toString().lowercase(Locale.ROOT)
		if(name.endsWith(".$extension")){
			return false
		}
		return PHOTO_EXTENSIONS.any { name.endsWith(".$it") }
	}

	override fun createConverter(tools: ConversionTools, probeResult: FFprobeResult?, input: Path, output: Path): Converter =
		CommandConverter(tools, listOf(
			tools.magick,
			input.toAbsolutePath().toString(),
			output.toAbsolutePath().toString(),
		))

	private companion object{
		val PHOTO_EXTENSIONS = setOf("heic", "tiff", "jpg", "raf")
	}
}
