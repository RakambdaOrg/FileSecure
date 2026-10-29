package fr.rakambda.filesecure.pipeline

import com.github.kokorin.jaffree.ffprobe.FFprobe
import fr.rakambda.filesecure.config.ConflictStrategy
import fr.rakambda.filesecure.config.Mapping
import fr.rakambda.filesecure.config.Operation
import fr.rakambda.filesecure.config.Pipeline
import fr.rakambda.filesecure.conversion.ConversionTools
import fr.rakambda.filesecure.conversion.LengthMoreThan
import fr.rakambda.filesecure.conversion.ProcessorType
import fr.rakambda.filesecure.metadata.MetadataExtractor
import fr.rakambda.filesecure.stage.Convert
import fr.rakambda.filesecure.stage.UnhandledStrategy
import fr.rakambda.filesecure.storage.H2Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import kotlin.io.path.createDirectories
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

/**
 * Runs real conversions, skipped when ffmpeg isn't installed.
 */
class ConversionPipelineTest{
	@TempDir
	lateinit var root: Path

	private val input by lazy { root.resolve("in").createDirectories() }
	private val output by lazy { root.resolve("out") }
	private val temp by lazy { root.resolve("temp") }

	@BeforeEach
	fun requireFfmpeg(){
		assumeTrue(commandSucceeds("ffmpeg", "-version"), "ffmpeg is not installed")
	}

	@Test
	fun convertsAndMovesAudio(){
		val mp3 = generateMp3(input.resolve("music/song.mp3"))
		val modified = mp3.getLastModifiedTime()
		val text = input.resolve("music/notes.txt").also { it.writeText("notes") }

		val stats = run(convertPipeline(Operation.MOVE, Convert(processors = listOf(ProcessorType.MP3_TO_AAC))))

		assertThat(stats.describe(false)).isEqualTo("2 found, 1 converted, 1 skipped by stages, 0 errors")
		val converted = output.resolve("music/song.m4a")
		assertThat(converted).exists()
		assertThat(codecs(converted)).containsExactly("aac")
		assertThat(converted.getLastModifiedTime()).describedAs("times copied from source").isEqualTo(modified)
		assertThat(mp3).doesNotExist()
		assertThat(text).describedAs("not convertible files are skipped by default").exists()
		assertThat(output.resolve("music/notes.txt")).doesNotExist()
		assertThat(temp.listDirectoryEntries()).describedAs("temporary files cleaned").isEmpty()
	}

	@Test
	fun passesNotConvertibleFiles(){
		generateMp3(input.resolve("song.mp3"))
		input.resolve("notes.txt").writeText("notes")

		val stats = run(convertPipeline(Operation.COPY, Convert(processors = listOf(ProcessorType.MP3_TO_AAC), unhandled = UnhandledStrategy.PASS)))

		assertThat(stats.describe(false)).isEqualTo("2 found, 1 copied, 1 converted, 0 errors")
		assertThat(output.resolve("song.m4a")).exists()
		assertThat(output.resolve("notes.txt")).hasContent("notes")
		assertThat(input.resolve("song.mp3")).exists()
	}

	@Test
	fun skipsConversionWhenDestinationExists(){
		generateMp3(input.resolve("song.mp3"))
		val existing = output.createDirectories().resolve("song.m4a").also { it.writeText("existing") }

		run(convertPipeline(Operation.MOVE, Convert(processors = listOf(ProcessorType.MP3_TO_AAC))).copy(conflict = ConflictStrategy.SKIP))

		assertThat(existing).hasContent("existing")
		assertThat(input.resolve("song.mp3")).exists()
	}

	@Test
	fun probeFiltersSkipShortMedia(){
		generateMp3(input.resolve("song.mp3"))

		run(convertPipeline(Operation.COPY, Convert(processors = listOf(ProcessorType.MP3_TO_AAC), filters = listOf(LengthMoreThan(60)))))

		assertThat(output).doesNotExist()
	}

	@Test
	fun dryRunDoesNotConvert(){
		val mp3 = generateMp3(input.resolve("song.mp3"))

		val stats = run(convertPipeline(Operation.MOVE, Convert(processors = listOf(ProcessorType.MP3_TO_AAC))), dryRun = true)

		assertThat(stats.describe(true)).isEqualTo("1 found, 1 would convert, 0 errors")
		assertThat(mp3).exists()
		assertThat(output).doesNotExist()
	}

	@Test
	fun replacesSourceInPlace(){
		val video = input.resolve("video.mkv")
		ffmpeg("-f", "lavfi", "-i", "testsrc=duration=1:size=64x64:rate=10", "-c:v", "libx264", video.toString())

		run(Pipeline(
			mappings = listOf(Mapping(input, input)),
			stages = listOf(Convert(processors = listOf(ProcessorType.VIDEO_TO_HEVC_MKV))),
			operation = Operation.MOVE,
			temp = temp,
		))

		assertThat(codecs(video)).containsExactly("hevc")
		assertThat(input.listDirectoryEntries()).containsExactly(video)
	}

	@Test
	fun cancellationStopsRunningConversion(): Unit = runBlocking {
		val video = input.resolve("long.mp4")
		ffmpeg("-f", "lavfi", "-i", "testsrc=duration=60:size=640x360:rate=30", "-c:v", "libx264", "-preset", "ultrafast", video.toString())
		val environment = RunEnvironment(FileOperations(false), MetadataExtractor({ _, _ -> null }), ConversionTools())

		val stats = PipelineStats()

		val job = launch(Dispatchers.Default) {
			PipelineRunner(environment).run(convertPipeline(Operation.MOVE, Convert(processors = listOf(ProcessorType.VIDEO_TO_HEVC_MKV))), "test", stats)
		}
		withTimeout(30.seconds) {
			while(childFfmpegCount() == 0L){
				delay(100)
			}
		}
		job.cancelAndJoin()

		withTimeout(5.seconds) {
			while(childFfmpegCount() > 0){
				delay(100)
			}
		}
		assertThat(video).exists()
		assertThat(output).doesNotExist()
		assertThat(temp.listDirectoryEntries()).isEmpty()
		assertThat(stats.errorCount).describedAs("cancellation is not an error").isZero()
		assertThat(stats.foundCount).isEqualTo(1)
	}

	private fun childFfmpegCount() = ProcessHandle.current().descendants()
		.filter { it.info().command().orElse("").endsWith("ffmpeg") }
		.count()

	@Test
	fun convertsTiffWithImageMagick(){
		assumeTrue(commandSucceeds("convert", "-version"), "ImageMagick is not installed")
		val tiff = input.resolve("image.tiff")
		assertThat(commandSucceeds("convert", "-size", "8x8", "xc:red", tiff.toString())).isTrue()

		run(convertPipeline(Operation.COPY, Convert(processors = listOf(ProcessorType.TIFF_TO_JPG))), magick = "convert")

		assertThat(codecs(output.resolve("image.jpg"))).containsExactly("mjpeg")
	}

	@Test
	fun cachesNotConvertibleFiles(){
		val text = input.resolve("notes.txt").also { it.writeText("notes") }
		val video = input.resolve("video.mkv").also { it.writeText("not really a video") }
		val database = root.resolve("cache/db")

		run(convertPipeline(Operation.COPY, Convert(cache = database)))

		H2Storage(database).use {
			assertThat(it.isUseless(text, Timestamp(Files.getLastModifiedTime(text).toMillis()))).isTrue()
			assertThat(it.isUseless(video, Timestamp(Files.getLastModifiedTime(video).toMillis()))).isTrue()
		}
	}

	private fun convertPipeline(operation: Operation, convert: Convert) = Pipeline(
		mappings = listOf(Mapping(input, output)),
		stages = listOf(convert),
		operation = operation,
		temp = temp,
	)

	private fun run(pipeline: Pipeline, dryRun: Boolean = false, magick: String = "magick"): PipelineStats = runBlocking {
		val environment = RunEnvironment(
			fileOperations = FileOperations(dryRun),
			metadataExtractor = MetadataExtractor({ _, _ -> null }),
			tools = ConversionTools(magick = magick),
			conversionThreads = 2,
		)
		PipelineRunner(environment).run(pipeline, "test")
	}

	private fun generateMp3(path: Path): Path{
		path.parent.createDirectories()
		ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:a", "libmp3lame", path.toString())
		return path
	}

	private fun ffmpeg(vararg args: String){
		assertThat(commandSucceeds("ffmpeg", "-hide_banner", "-loglevel", "error", *args)).isTrue()
	}

	private fun codecs(path: Path): List<String> = FFprobe.atPath()
		.setShowStreams(true)
		.setInput(path.toString())
		.execute()
		.streams
		.map { it.codecName }

	private fun commandSucceeds(vararg command: String): Boolean = try{
		ProcessBuilder(*command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0
	}
	catch(_: Exception){
		false
	}
}
