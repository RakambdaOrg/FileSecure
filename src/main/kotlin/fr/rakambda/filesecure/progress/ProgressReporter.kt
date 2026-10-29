package fr.rakambda.filesecure.progress

import me.tongfei.progressbar.InteractiveConsoleProgressBarConsumer
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

interface ProgressReporter : AutoCloseable{
	fun fileFound()

	fun fileProcessed()

	fun conversionQueued()

	fun conversionDone()

	/**
	 * Starts tracking a single task, closing it releases its display.
	 */
	fun task(name: String): TaskProgress

	override fun close(){
	}
}

interface TaskProgress : AutoCloseable{
	fun setMax(max: Long)

	fun stepTo(value: Long)

	override fun close(){
	}
}

object NoOpProgressReporter : ProgressReporter{
	private object NoOpTask : TaskProgress{
		override fun setMax(max: Long){
		}

		override fun stepTo(value: Long){
		}
	}

	override fun fileFound(){
	}

	override fun fileProcessed(){
	}

	override fun conversionQueued(){
	}

	override fun conversionDone(){
	}

	override fun task(name: String): TaskProgress = NoOpTask
}

/**
 * Console progress bars: one for files, one for conversions, and one per running conversion, reused once their conversion ends.
 */
class ConsoleProgressReporter : ProgressReporter{
	private val filesFound = AtomicLong()
	private val conversionsQueued = AtomicLong()
	private val filesBar = ProgressBarBuilder()
		.setTaskName("Files")
		.setUnit(" files", 1)
		.setInitialMax(0)
		.hideEta()
		.showSpeed()
		.build()
	private val conversionsBar = ProgressBarBuilder()
		.setTaskName("Conversions")
		.setInitialMax(0)
		.build()
	private val taskCounter = AtomicInteger()
	private val idleTaskBars = ConcurrentLinkedQueue<ProgressBar>()

	override fun fileFound(){
		filesBar.maxHint(filesFound.incrementAndGet())
	}

	override fun fileProcessed(){
		filesBar.step()
	}

	override fun conversionQueued(){
		conversionsBar.maxHint(conversionsQueued.incrementAndGet())
	}

	override fun conversionDone(){
		conversionsBar.step()
	}

	override fun task(name: String): TaskProgress{
		val bar = (idleTaskBars.poll() ?: createTaskBar()).resume()
		bar.extraMessage = name
		return object : TaskProgress{
			override fun setMax(max: Long){
				bar.maxHint(max)
			}

			override fun stepTo(value: Long){
				bar.stepTo(value)
			}

			override fun close(){
				idleTaskBars.offer(bar.setExtraMessage("").maxHint(-1).reset().pause())
			}
		}
	}

	private fun createTaskBar(): ProgressBar = ProgressBarBuilder()
		.setTaskName("Converter ${taskCounter.incrementAndGet()}")
		.setUnit("s", 1000)
		.setSpeedUnit(ChronoUnit.SECONDS)
		.setConsumer(InteractiveConsoleProgressBarConsumer(PrintStream(FileOutputStream(FileDescriptor.err)), -1))
		.clearDisplayOnFinish()
		.build()

	override fun close(){
		idleTaskBars.forEach(ProgressBar::close)
		idleTaskBars.clear()
		conversionsBar.close()
		filesBar.close()
	}
}
