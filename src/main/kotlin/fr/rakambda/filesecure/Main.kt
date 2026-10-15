package fr.rakambda.filesecure

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import fr.rakambda.filesecure.config.Configuration
import fr.rakambda.filesecure.console.ConsoleHandler
import fr.rakambda.filesecure.console.PauseGate
import fr.rakambda.filesecure.conversion.ConversionTools
import fr.rakambda.filesecure.conversion.WindowsAffinity
import fr.rakambda.filesecure.metadata.GeonamesTimeZoneResolver
import fr.rakambda.filesecure.metadata.MetadataExtractor
import fr.rakambda.filesecure.pipeline.FileOperations
import fr.rakambda.filesecure.pipeline.PipelineRunner
import fr.rakambda.filesecure.pipeline.RunEnvironment
import fr.rakambda.filesecure.progress.ConsoleProgressReporter
import fr.rakambda.filesecure.progress.NoOpProgressReporter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

// Lazy so the startup message setting in main is applied before kotlin-logging initializes
private val log by lazy { KotlinLogging.logger {} }

class FileSecureCommand : CliktCommand(name = "filesecure"){
	private val configurationFile by option("-c", "--config", help = "The path to the configuration file")
		.path(mustExist = true, canBeDir = false, mustBeReadable = true)
		.required()
	private val dryRun by option("--dry", help = "Performs a dry run, nothing is written")
		.flag()
	private val threads by option("-t", "--threads", help = "How many conversions are performed in parallel")
		.int()
		.default(1)
		.check("must be >= 1") { it >= 1 }
	private val workers by option("-w", "--workers", help = "How many files are processed in parallel (scan, probe, copy)")
		.int()
		.default(8)
		.check("must be >= 1") { it >= 1 }
	private val ffmpegThreads by option("-ft", "--ffmpeg-threads", help = "Value of ffmpeg's -threads option for each conversion")
		.int()
		.check("must be >= 0") { it >= 0 }
	private val ffmpegDirectory by option("--ffmpeg", help = "Folder containing the ffmpeg executable, uses the PATH if not set")
		.path(mustExist = true, canBeFile = false)
	private val ffprobeDirectory by option("--ffprobe", help = "Folder containing the ffprobe executable, uses the PATH if not set")
		.path(mustExist = true, canBeFile = false)
	private val magick by option("--magick", help = "ImageMagick executable used for photo conversions")
		.default("magick")
	private val affinityMask by option("--affinity", help = "Windows only, mask of the CPU cores conversions can run on, e.g. 7 for cores 1, 2 and 3")
		.long()
		.check("must be > 0") { it > 0 }
	private val progress by option("--progress", help = "Show progress bars, enabled by default when running in a terminal")
		.flag("--no-progress", default = System.console()?.isTerminal == true)
	private val geonamesUsername by option("--geonames-username", help = "geonames.org account used to find the time zone of geotagged media")
		.default("mrcraftcod")

	override fun help(context: Context): String = "Moves, backs up and converts files through configurable pipelines."

	override fun run(){
		val configuration = try{
			Configuration.load(configurationFile)
		}
		catch(e: Exception){
			log.error(e) { "Failed to load configuration from $configurationFile" }
			throw ProgramResult(1)
		}

		val pauseGate = PauseGate()
		val affinity = affinityMask?.let(::WindowsAffinity)
		val progressReporter = if(progress) ConsoleProgressReporter() else NoOpProgressReporter
		progressReporter.use {
			val environment = RunEnvironment(
				fileOperations = FileOperations(dryRun),
				metadataExtractor = MetadataExtractor(GeonamesTimeZoneResolver(geonamesUsername)),
				tools = ConversionTools(ffmpegDirectory, ffprobeDirectory, magick, ffmpegThreads, affinity?.let { it::apply }),
				conversionThreads = threads,
				workers = workers,
				pauseGate = pauseGate,
				progress = progressReporter,
			)
			runPipelines(configuration, environment, pauseGate)
		}
	}

	private fun runPipelines(configuration: Configuration, environment: RunEnvironment, pauseGate: PauseGate) = runBlocking {
		val runner = PipelineRunner(environment)
		val job = launch(Dispatchers.Default) {
			configuration.pipelines.forEachIndexed { index, pipeline ->
				val name = pipeline.displayName(index)
				if(!pipeline.enabled){
					log.info { "Pipeline $name is disabled" }
					return@forEachIndexed
				}
				try{
					runner.run(pipeline, name)
				}
				catch(e: CancellationException){
					throw e
				}
				catch(e: Exception){
					log.error(e) { "Failed to run pipeline $name" }
				}
			}
		}

		ConsoleHandler(System.`in`, pauseGate) { job.cancel() }.start()
		val shutdownHook = Thread {
			job.cancel()
			// Give running conversions the time to be stopped and their temporary files removed
			runBlocking { withTimeoutOrNull(30.seconds) { job.join() } }
		}
		Runtime.getRuntime().addShutdownHook(shutdownHook)

		job.join()
		try{
			Runtime.getRuntime().removeShutdownHook(shutdownHook)
		}
		catch(_: IllegalStateException){
			// Already shutting down
		}
	}
}

fun main(args: Array<String>){
	System.setProperty("kotlin-logging.logStartupMessage", "false")
	FileSecureCommand().main(args)
}
