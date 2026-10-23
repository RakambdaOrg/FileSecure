package fr.rakambda.filesecure.pipeline

import java.util.EnumMap
import java.util.concurrent.atomic.AtomicLong

/**
 * What happened to a file that went through a pipeline.
 */
enum class FileOutcome(private val label: String, private val dryRunLabel: String){
	MOVED("moved", "would move"),
	COPIED("copied", "would copy"),
	CONVERTED("converted", "would convert"),
	/**
	 * Accepted by every stage in a pipeline whose operation is NONE.
	 */
	PROCESSED("would process (operation NONE)", "would process (operation NONE)"),
	SKIPPED_BY_STAGE("skipped by stages", "skipped by stages"),
	DESTINATION_EXISTS("destination exists", "destination exists"),
	ALREADY_IN_PLACE("already in place", "already in place");

	fun label(dryRun: Boolean): String = if(dryRun) dryRunLabel else label
}

/**
 * Counters of a pipeline run, updated concurrently by the workers.
 */
class PipelineStats{
	private val found = AtomicLong()
	private val errors = AtomicLong()
	private val outcomes = EnumMap<FileOutcome, AtomicLong>(FileOutcome::class.java).apply {
		FileOutcome.entries.forEach { put(it, AtomicLong()) }
	}

	val foundCount: Long
		get() = found.get()

	val errorCount: Long
		get() = errors.get()

	fun fileFound(){
		found.incrementAndGet()
	}

	fun record(outcome: FileOutcome){
		outcomes.getValue(outcome).incrementAndGet()
	}

	fun error(){
		errors.incrementAndGet()
	}

	fun count(outcome: FileOutcome): Long = outcomes.getValue(outcome).get()

	operator fun plusAssign(other: PipelineStats){
		found.addAndGet(other.foundCount)
		errors.addAndGet(other.errorCount)
		FileOutcome.entries.forEach { outcomes.getValue(it).addAndGet(other.count(it)) }
	}

	/**
	 * Counters separated by commas, zero outcomes being omitted, e.g. `12 found, 10 moved, 2 skipped by stages, 0 errors`.
	 */
	fun describe(dryRun: Boolean): String{
		val parts = mutableListOf("$foundCount found")
		FileOutcome.entries.forEach { outcome ->
			count(outcome).takeIf { it > 0 }?.let { parts += "$it ${outcome.label(dryRun)}" }
		}
		parts += "$errorCount errors"
		return parts.joinToString(", ")
	}
}
