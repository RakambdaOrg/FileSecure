package fr.rakambda.filesecure.console

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream

private val log = KotlinLogging.logger {}

/**
 * Reads commands from the standard input: `p` pauses, `r` resumes and `q` quits.
 */
class ConsoleHandler(
	private val input: InputStream,
	private val pauseGate: PauseGate,
	private val onQuit: () -> Unit,
) : Thread("Console watcher"){
	init{
		isDaemon = true
	}

	override fun run(){
		try{
			input.bufferedReader().lineSequence().forEach { line ->
				when(line.trim()){
					"q" -> {
						log.info { "Exiting" }
						onQuit()
					}
					"p" -> {
						log.info { "Pausing, running conversions will finish" }
						pauseGate.pause()
					}
					"r" -> {
						log.info { "Resuming" }
						pauseGate.resume()
					}
				}
			}
		}
		catch(e: Exception){
			log.warn(e) { "Error reading console commands" }
		}
	}
}
