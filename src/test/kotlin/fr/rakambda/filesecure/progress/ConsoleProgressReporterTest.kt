package fr.rakambda.filesecure.progress

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
		}

		assertThat(System.out).isSameAs(out)
		val output = recorder.output()
		assertThat(output).contains("a log line")
		val bars = output.substring(output.lastIndexOf("Overall"))
		assertThat(output.indexOf("a log line")).isLessThan(output.lastIndexOf("Overall"))
		assertThat(bars).contains("1/2", "Scanning", "Conversions", "0/1", "video.mp4", "50%").doesNotContain("removed.mp4")
	}
}
