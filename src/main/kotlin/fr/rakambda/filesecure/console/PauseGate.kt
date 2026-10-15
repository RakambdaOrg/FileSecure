package fr.rakambda.filesecure.console

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Lets work be paused and resumed, work already started is not interrupted.
 */
class PauseGate{
	private val paused = MutableStateFlow(false)

	val isPaused: Boolean
		get() = paused.value

	fun pause(){
		paused.value = true
	}

	fun resume(){
		paused.value = false
	}

	/**
	 * Suspends while paused.
	 */
	suspend fun awaitResumed(){
		paused.first { !it }
	}
}
