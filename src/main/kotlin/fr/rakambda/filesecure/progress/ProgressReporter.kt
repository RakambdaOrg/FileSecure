package fr.rakambda.filesecure.progress

import com.github.ajalt.mordant.animation.progress.MultiProgressBarAnimation
import com.github.ajalt.mordant.animation.progress.advance
import com.github.ajalt.mordant.animation.progress.removeTask
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.widgets.progress.percentage
import com.github.ajalt.mordant.widgets.progress.progressBar
import com.github.ajalt.mordant.widgets.progress.progressBarContextLayout
import com.github.ajalt.mordant.widgets.progress.text
import com.github.ajalt.mordant.widgets.progress.timeRemaining
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.util.Timer
import kotlin.concurrent.timer

interface ProgressReporter : AutoCloseable{
	fun fileFound()

	/**
	 * The scan of an input folder ended, [fileFound] starts the next one.
	 */
	fun scanDone()

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

	override fun scanDone(){
	}

	override fun fileProcessed(){
	}

	override fun conversionQueued(){
	}

	override fun conversionDone(){
	}

	override fun task(name: String): TaskProgress = NoOpTask
}

private const val TASK_NAME_LENGTH = 40

// The bright tip of a bar, it has to stand out from both the completed and the remaining parts
private val BAR_CURSOR = TextColors.brightYellow + TextStyles.bold

private val countLayout = progressBarContextLayout<String> {
	text { context }
	progressBar(separatorStyle = BAR_CURSOR)
	text { total?.let { "$completed/$it" } ?: "$completed" }
	timeRemaining()
}
private val taskLayout = progressBarContextLayout<String> {
	text { "  ${context.take(TASK_NAME_LENGTH)}" }
	progressBar(separatorStyle = BAR_CURSOR)
	percentage()
	timeRemaining()
}

/**
 * Console progress bars kept at the bottom of the terminal: the overall progress, one per category (scan, conversions) and one per running conversion.
 * While open, the standard output and error are printed above the bars instead of over them.
 */
class ConsoleProgressReporter(terminal: Terminal = stdoutTerminal()) : ProgressReporter{
	private val out = System.out
	private val err = System.err
	private val animation = MultiProgressBarAnimation(terminal)
	private val overall = animation.addTask(countLayout, "Overall", total = 0)
	private val scan = animation.addTask(countLayout, "Scanning", total = 0)
	private val conversions = animation.addTask(countLayout, "Conversions", total = 0, visible = false)
	// Mordant doesn't serialize prints and bar redraws, done concurrently they leave duplicated bars behind
	private val lock = Any()
	private var closed = false
	private val streams = listOf(TerminalStream(terminal, lock), TerminalStream(terminal, lock))
	private val refresher: Timer

	init{
		// The animation stops itself once every task is finished, e.g. when all the files found so far are processed. This hidden task never finishes.
		animation.addTask(countLayout, "", visible = false)
		System.setOut(PrintStream(streams[0], true))
		System.setErr(PrintStream(streams[1], true))
		// Started last, it needs the hidden task and the lock
		refresher = timer(daemon = true, period = REFRESH_PERIOD) { refresh(false) }
	}

	override fun fileFound(){
		overall.update { total = (total ?: 0) + 1 }
		// No total while scanning, how many files are left to find is unknown
		scan.update {
			completed += 1
			total = null
		}
	}

	override fun scanDone(){
		scan.update { total = completed }
	}

	override fun fileProcessed(){
		overall.advance()
	}

	override fun conversionQueued(){
		conversions.update {
			visible = true
			total = (total ?: 0) + 1
		}
	}

	override fun conversionDone(){
		conversions.advance()
	}

	override fun task(name: String): TaskProgress{
		val task = animation.addTask(taskLayout, name)
		return object : TaskProgress{
			override fun setMax(max: Long){
				task.update { total = max }
			}

			override fun stepTo(value: Long){
				task.update { completed = value }
			}

			override fun close(){
				animation.removeTask(task)
			}
		}
	}

	private fun refresh(last: Boolean) = synchronized(lock) {
		// A refresh running while the reporter closes would draw the bars again
		if(closed){
			return
		}
		animation.refresh(last)
		if(last){
			animation.stop()
			closed = true
		}
	}

	override fun close(){
		refresher.cancel()
		refresh(true)
		System.setOut(out)
		System.setErr(err)
		// Not closed: a thread that read System.out before it was restored can still print through them
		streams.forEach(TerminalStream::drain)
	}

	private companion object{
		const val REFRESH_PERIOD = 100L
	}
}

/**
 * Terminal writing to the current standard output, even once [System.out] is replaced.
 */
private fun stdoutTerminal(): Terminal{
	val out = System.out
	return Terminal(terminalInterface = object : TerminalInterface by Terminal().terminalInterface{
		override fun completePrintRequest(request: PrintRequest){
			out.print(request.text)
			if(request.trailingLinebreak){
				out.println()
			}
			out.flush()
		}
	})
}

/**
 * Prints complete lines through the terminal, which redraws its bars under them. An unfinished line is kept until its line break, bars can't be drawn after it.
 */
private class TerminalStream(private val terminal: Terminal, private val lock: Any) : ByteArrayOutputStream(){
	@Synchronized
	override fun flush(){
		val end = (count - 1 downTo 0).firstOrNull { buf[it] == '\n'.code.toByte() }?.plus(1) ?: return
		val rest = buf.copyOfRange(end, count)
		synchronized(lock) { terminal.rawPrint(String(buf, 0, end, Charset.defaultCharset())) }
		reset()
		writeBytes(rest)
	}

	/**
	 * Prints everything left, an unfinished line included.
	 */
	@Synchronized
	fun drain(){
		if(count > 0 && buf[count - 1] != '\n'.code.toByte()){
			write('\n'.code)
		}
		flush()
	}
}
