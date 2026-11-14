package fr.rakambda.filesecure.progress

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class ConsoleProgressReporterTest{
	@Test
	fun `output is printed above the bars and the streams are restored`(){
		val out = System.out
		val recorder = TerminalRecorder(width = 120)

		ConsoleProgressReporter(Terminal(terminalInterface = recorder)).use { progress ->
			progress.fileFound()
			progress.fileFound()
			progress.fileProcessed()
			print("a log")
			assertThat(recorder.output()).doesNotContain("a log")
			println(" line")

			progress.conversionQueued()
			progress.task("removed.mp4").close()
			val task = progress.task("video.mp4")
			task.setMax(10)
			task.stepTo(5)
			print("unfinished line")
		}

		assertThat(System.out).isSameAs(out)
		val output = recorder.output()
		assertThat(output).contains("a log line", "unfinished line")
		val bars = output.substring(output.lastIndexOf("Overall"))
		assertThat(output.indexOf("a log line")).isLessThan(output.lastIndexOf("Overall"))
		assertThat(bars).contains("1/2", "Scanning", "Conversions", "0/1", "video.mp4", "50%").doesNotContain("removed.mp4")
	}

	@Test
	fun `counts and lines are kept when reported from several threads`(){
		val threads = 8
		val iterations = 500
		val total = threads * iterations
		val recorder = TerminalRecorder(width = 120)

		ConsoleProgressReporter(Terminal(terminalInterface = recorder)).use { progress ->
			(0 until threads).map { thread ->
				thread {
					repeat(iterations) { iteration ->
						progress.fileFound()
						progress.conversionQueued()
						progress.task("task-$thread-$iteration").use {
							it.setMax(2)
							it.stepTo(1)
							println("line-$thread-$iteration")
						}
						progress.conversionDone()
						progress.fileProcessed()
					}
				}
			}.forEach(Thread::join)
			progress.scanDone()
		}

		val output = recorder.output()
		// Each line follows the cursor moves erasing the bars, so it doesn't start its terminal line
		val lines = Regex("line-\\d+-\\d+(?=\\n)").findAll(output).map { it.value }.toList()
		assertThat(lines).hasSize(total).doesNotHaveDuplicates()
		val bars = output.substring(output.lastIndexOf("Overall")).lines()
		assertThat(bars.filter { it.isNotBlank() }).hasSize(3).allSatisfy { assertThat(it).contains("$total/$total") }
	}
}
