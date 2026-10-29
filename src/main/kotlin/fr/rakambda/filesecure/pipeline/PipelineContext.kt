package fr.rakambda.filesecure.pipeline

import fr.rakambda.filesecure.config.Pipeline
import fr.rakambda.filesecure.console.PauseGate
import fr.rakambda.filesecure.conversion.ConversionTools
import fr.rakambda.filesecure.metadata.MetadataExtractor
import fr.rakambda.filesecure.progress.NoOpProgressReporter
import fr.rakambda.filesecure.progress.ProgressReporter
import kotlinx.coroutines.sync.Semaphore

/**
 * Everything shared by all pipelines of a run.
 *
 * @param conversionThreads Maximum number of conversions running at the same time.
 * @param workers Maximum number of files processed at the same time.
 */
class RunEnvironment(
	val fileOperations: FileOperations,
	val metadataExtractor: MetadataExtractor,
	val tools: ConversionTools,
	conversionThreads: Int = 1,
	val workers: Int = 8,
	val pauseGate: PauseGate = PauseGate(),
	val progress: ProgressReporter = NoOpProgressReporter,
){
	val conversionLock = Semaphore(conversionThreads)

	val dryRun: Boolean
		get() = fileOperations.dryRun
}

class PipelineContext(
	val environment: RunEnvironment,
	val pipeline: Pipeline,
)
