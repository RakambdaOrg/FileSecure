package fr.rakambda.filesecure.metadata

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the log events of every logger while attached.
 */
internal class MemoryAppender : AbstractAppender("memory", null, null, true, Property.EMPTY_ARRAY){
	private val events = CopyOnWriteArrayList<LogEvent>()

	override fun append(event: LogEvent){
		events += event.toImmutable()
	}

	fun problems(): List<LogEvent> = events.filter { it.level.isMoreSpecificThan(Level.WARN) }

	fun attach(){
		val context = LoggerContext.getContext(false)
		start()
		context.configuration.rootLogger.addAppender(this, Level.ALL, null)
		context.updateLoggers()
	}

	fun detach(){
		val context = LoggerContext.getContext(false)
		context.configuration.rootLogger.removeAppender(name)
		context.updateLoggers()
		stop()
	}
}
