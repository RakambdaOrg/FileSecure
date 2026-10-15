package fr.rakambda.filesecure.conversion

import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.process.Stopper
import java.nio.file.Path

/**
 * FFmpeg notifying when its process starts and exposing its command line.
 */
class CustomFFmpeg(
	private val executable: Path,
	private val processListener: ((Process) -> Unit)?,
) : FFmpeg(executable){
	override fun createStopper(): Stopper{
		val delegate = super.createStopper()
		return object : Stopper by delegate{
			override fun setProcess(process: Process){
				delegate.setProcess(process)
				processListener?.invoke(process)
			}
		}
	}

	public override fun buildArguments(): List<String> = super.buildArguments()

	val commandLine: String
		get() = (listOf(executable.toString()) + buildArguments()).joinToString(" ") { "\"$it\"" }
}
