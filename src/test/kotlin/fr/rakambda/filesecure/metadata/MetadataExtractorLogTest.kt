package fr.rakambda.filesecure.metadata

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.random.Random

class MetadataExtractorLogTest{
	@TempDir
	lateinit var root: Path

	private val extractor = MetadataExtractor({ _, _ -> null })
	private val appender = MemoryAppender()

	@BeforeEach
	fun attachAppender(){
		val context = LoggerContext.getContext(false)
		appender.start()
		context.configuration.rootLogger.addAppender(appender, Level.ALL, null)
		context.updateLoggers()
	}

	@AfterEach
	fun detachAppender(){
		val context = LoggerContext.getContext(false)
		context.configuration.rootLogger.removeAppender(appender.name)
		context.updateLoggers()
		appender.stop()
	}

	@Test
	fun unsupportedFilesLogNoProblem(){
		val text = Files.writeString(root.resolve("notes.txt"), "hello")
		val empty = Files.createFile(root.resolve("empty.jpg"))

		assertThat(extractor.getMetadata(text).takenDate).isNull()
		assertThat(extractor.getMetadata(empty).takenDate).isNull()

		assertThat(appender.problems()).isEmpty()
	}

	@Test
	fun jpegWithoutDateLogsNoProblem(){
		val jpeg = Files.write(root.resolve("photo.jpg"), jpegBytes())
		// Cut in the image data, after the headers: metadata-extractor reads the headers without failing
		val cut = Files.write(root.resolve("cut.jpg"), jpegBytes().let { it.copyOf(it.size / 2) })

		assertThat(extractor.getMetadata(jpeg).takenDate).isNull()
		assertThat(extractor.getMetadata(cut).takenDate).isNull()

		assertThat(appender.problems()).isEmpty()
	}

	@Test
	fun truncatedJpegLogsWarning(){
		val truncated = Files.write(root.resolve("truncated.jpg"), jpegBytes().copyOf(10))

		assertThat(extractor.getMetadata(truncated).takenDate).isNull()

		assertThat(appender.problems()).singleElement().satisfies({
			assertThat(it.level).isEqualTo(Level.WARN)
			assertThat(it.message.formattedMessage).contains(truncated.toString(), "EOFException")
		})
	}

	private fun jpegBytes(): ByteArray{
		val output = ByteArrayOutputStream()
		val random = Random(42)
		val image = BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB)
		for(x in 0..<image.width){
			for(y in 0..<image.height){
				image.setRGB(x, y, random.nextInt())
			}
		}
		ImageIO.write(image, "jpg", output)
		return output.toByteArray()
	}

	private class MemoryAppender : AbstractAppender("memory", null, null, true, Property.EMPTY_ARRAY){
		private val events = CopyOnWriteArrayList<LogEvent>()

		override fun append(event: LogEvent){
			events += event.toImmutable()
		}

		fun problems(): List<LogEvent> = events.filter { it.level.isMoreSpecificThan(Level.WARN) }
	}
}
