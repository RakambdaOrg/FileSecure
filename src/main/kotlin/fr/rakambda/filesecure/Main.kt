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
import fr.rakambda.filesecure.metadata.MetadataExtractor
import fr.rakambda.filesecure.metadata.OfflineTimeZoneResolver
import fr.rakambda.filesecure.pipeline.FileOperations
import fr.rakambda.filesecure.pipeline.PipelineRunner
import fr.rakambda.filesecure.pipeline.PipelineStats
import fr.rakambda.filesecure.pipeline.RunEnvironment
import fr.rakambda.filesecure.progress.ConsoleProgressReporter
import fr.rakambda.filesecure.progress.NoOpProgressReporter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
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
		val report = RunReport(configuration, dryRun)
		val completed = progressReporter.use {
			val environment = RunEnvironment(
				fileOperations = FileOperations(dryRun),
				metadataExtractor = MetadataExtractor(OfflineTimeZoneResolver()),
				tools = ConversionTools(ffmpegDirectory, ffprobeDirectory, magick, ffmpegThreads, affinity?.let { it::apply }),
				conversionThreads = threads,
				workers = workers,
				pauseGate = pauseGate,
				progress = progressReporter,
			)
			runPipelines(configuration, environment, pauseGate, report)
		}
		if(!completed){
			// The JVM is shutting down, the shutdown hook logs the summary
			return
		}
		report.log()
		val code = exitCode(report.errors, report.stopped)
		if(code != 0){
			throw ProgramResult(code)
		}
	}

	/**
	 * @return false if the JVM is shutting down.
	 */
	private fun runPipelines(configuration: Configuration, environment: RunEnvironment, pauseGate: PauseGate, report: RunReport): Boolean = runBlocking {
		val runner = PipelineRunner(environment)
		val job = launch(Dispatchers.Default) {
			configuration.pipelines.forEachIndexed { index, pipeline ->
				val name = pipeline.displayName(index)
				if(!pipeline.enabled){
					log.info { "Pipeline $name is disabled" }
					return@forEachIndexed
				}
				val stats = report.start(index)
				try{
					runner.run(pipeline, name, stats)
				}
				catch(e: CancellationException){
					throw e
				}
				catch(e: Exception){
					log.error(e) { "Failed to run pipeline $name" }
					stats.error()
				}
			}
		}

		ConsoleHandler(System.`in`, pauseGate) {
			if(job.isActive){
				report.stopped = true
			}
			job.cancel()
		}.start()
		val shutdownHook = Thread {
			job.cancel()
			// Give running conversions the time to be stopped and their temporary files removed
			runBlocking { withTimeoutOrNull(30.seconds) { job.join() } }
			report.stopped = true
			report.log()
		}
		Runtime.getRuntime().addShutdownHook(shutdownHook)

		job.join()
		try{
			Runtime.getRuntime().removeShutdownHook(shutdownHook)
			true
		}
		catch(_: IllegalStateException){
			// Already shutting down
			false
		}
	}
}

/**
 * Counts of every enabled pipeline of a run.
 */
private class RunReport(private val configuration: Configuration, private val dryRun: Boolean){
	private val stats = arrayOfNulls<PipelineStats>(configuration.pipelines.size)

	/**
	 * Set when the run is stopped with the `q` command or by the JVM shutting down.
	 */
	@Volatile
	var stopped = false

	val errors: Long
		get() = stats.sumOf { it?.errorCount ?: 0 }

	@Synchronized
	fun start(index: Int): PipelineStats = PipelineStats().also { stats[index] = it }

	@Synchronized
	fun log(){
		val mode = if(dryRun) " (dry run)" else ""
		val total = PipelineStats()
		configuration.pipelines.forEachIndexed { index, pipeline ->
			if(!pipeline.enabled){
				return@forEachIndexed
			}
			val name = pipeline.displayName(index)
			val pipelineStats = stats[index]
			if(pipelineStats == null){
				log.info { "Pipeline '$name'$mode: not run" }
				return@forEachIndexed
			}
			total += pipelineStats
			val level = if(pipelineStats.errorCount > 0) Level.ERROR else Level.INFO
			log.at(level) { message = "Pipeline '$name'$mode: ${pipelineStats.describe(dryRun)}" }
		}
		val level = if(total.errorCount > 0) Level.ERROR else Level.INFO
		val suffix = if(stopped) ", stopped before the end" else ""
		log.at(level) { message = "Total$mode: ${total.describe(dryRun)}$suffix" }
	}
}

/**
 * @return 3 if the run was stopped with the `q` command, 2 if any error happened, 0 otherwise.
 */
internal fun exitCode(errors: Long, stopped: Boolean): Int = when{
	stopped -> 3
	errors > 0 -> 2
	else -> 0
}

fun main(args: Array<String>){
	System.setProperty("kotlin-logging.logStartupMessage", "false")
	FileSecureCommand().main(args)
}
