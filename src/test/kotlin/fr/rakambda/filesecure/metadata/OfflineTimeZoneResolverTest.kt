package fr.rakambda.filesecure.metadata

import net.iakovlev.timeshape.TimeZoneEngine
import org.apache.logging.log4j.Level
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

// One instance for all tests, so the time zone data is loaded once
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfflineTimeZoneResolverTest{
	@TempDir
	lateinit var root: Path

	private val engine by lazy { TimeZoneEngine.initialize() }
	private val resolver by lazy { OfflineTimeZoneResolver { engine } }
	private lateinit var appender: MemoryAppender

	@BeforeEach
	fun attachAppender(){
		appender = MemoryAppender()
		appender.attach()
	}

	@AfterEach
	fun detachAppender(){
		appender.detach()
	}

	@ParameterizedTest
	@CsvSource(
		"48.8566, 2.3522, Europe/Paris",
		"14.5995, 120.9842, Asia/Manila",
		"13.7563, 100.5018, Asia/Bangkok",
		"40.7128, -74.0060, America/New_York",
		"-33.8688, 151.2093, Australia/Sydney",
	)
	fun resolvesKnownPoints(latitude: Double, longitude: Double, zone: String){
		assertThat(resolver.resolve(latitude, longitude)).isEqualTo(ZoneId.of(zone))
		assertThat(appender.problems()).isEmpty()
	}

	@Test
	fun resolvesNauticalZoneAtSea(){
		// International waters are covered by 15° wide nautical zones, -160 is in the one of UTC-11 (POSIX sign in Etc names is inverted)
		assertThat(resolver.resolve(0.0, -160.0)).isEqualTo(ZoneId.of("Etc/GMT+11"))
		assertThat(appender.problems()).isEmpty()
	}

	@Test
	fun outOfRangeCoordinatesHaveNoZoneAndWarnOnce(){
		val resolver = OfflineTimeZoneResolver { engine }

		assertThat(resolver.resolve(95.0, 0.0)).isNull()
		assertThat(resolver.resolve(0.0, 200.0)).isNull()

		assertThat(appender.problems()).singleElement().satisfies({
			assertThat(it.level).isEqualTo(Level.WARN)
			assertThat(it.message.formattedMessage).contains("95.0;0.0")
		})
	}

	@Test
	fun loadsDataLazilyOnce(){
		val calls = AtomicInteger()
		val resolver = OfflineTimeZoneResolver {
			calls.incrementAndGet()
			Thread.sleep(100)
			engine
		}
		val extractor = MetadataExtractor(resolver)
		extractor.getMetadata(Files.write(root.resolve("photo.jpg"), jpegBytes()))
		assertThat(calls).hasValue(0)

		val threads = 8
		val start = CountDownLatch(1)
		val executor = Executors.newFixedThreadPool(threads)
		try{
			val zones = (1..threads).map {
				executor.submit<ZoneId?> {
					start.await()
					resolver.resolve(48.8566, 2.3522)
				}
			}
			start.countDown()
			assertThat(zones.map { it.get(1, TimeUnit.MINUTES) }).containsOnly(ZoneId.of("Europe/Paris"))
		}
		finally{
			executor.shutdownNow()
		}
		assertThat(resolver.resolve(13.7563, 100.5018)).isEqualTo(ZoneId.of("Asia/Bangkok"))
		assertThat(calls).hasValue(1)
	}

	@Test
	fun initializationFailureIsNotRetried(){
		val calls = AtomicInteger()
		val resolver = OfflineTimeZoneResolver {
			calls.incrementAndGet()
			throw UnsatisfiedLinkError("no zstd-jni in java.library.path")
		}

		assertThat(resolver.resolve(48.8566, 2.3522)).isNull()
		assertThat(resolver.resolve(14.5995, 120.9842)).isNull()

		assertThat(calls).hasValue(1)
		assertThat(appender.problems()).singleElement().satisfies({
			assertThat(it.level).isEqualTo(Level.WARN)
			assertThat(it.message.formattedMessage).contains("48.8566;2.3522", "UnsatisfiedLinkError")
		})
	}

	@Test
	fun extractorReadsExifDateInZoneOfGpsLocation(){
		// Manila, 14°35'58.20"N 120°59'03.12"E
		val jpeg = jpegBytes()
		val exif = exifSegment("2023:05:12 10:15:12", 'N', intArrayOf(14, 35, 5820), 'E', intArrayOf(120, 59, 312))
		val photo = Files.write(root.resolve("photo.jpg"), jpeg.copyOfRange(0, 2) + exif + jpeg.copyOfRange(2, jpeg.size))

		val metadata = MetadataExtractor(resolver).getMetadata(photo)

		assertThat(metadata.takenDate).isEqualTo(ZonedDateTime.of(2023, 5, 12, 10, 15, 12, 0, ZoneId.of("Asia/Manila")))
		assertThat(appender.problems()).isEmpty()
	}

	private fun jpegBytes(): ByteArray{
		val output = ByteArrayOutputStream()
		ImageIO.write(BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpg", output)
		return output.toByteArray()
	}

	/**
	 * Builds an APP1 segment with a big endian TIFF structure: IFD0 pointing to an Exif IFD (DateTimeOriginal) and a GPS IFD.
	 * Coordinates are given as degrees, minutes and hundredths of seconds.
	 */
	private fun exifSegment(date: String, latitudeRef: Char, latitude: IntArray, longitudeRef: Char, longitude: IntArray): ByteArray{
		val tiff = ByteBuffer.allocate(178)
		tiff.put("MM".toByteArray()).putShort(42).putInt(8)
		tiff.putShort(2)
		tiff.entry(0x8769, LONG, 1, 38)
		tiff.entry(0x8825, LONG, 1, 76)
		tiff.putInt(0)
		tiff.putShort(1)
		tiff.entry(0x9003, ASCII, 20, 56)
		tiff.putInt(0)
		tiff.put("$date\u0000".toByteArray())
		tiff.putShort(4)
		tiff.entry(0x0001, ASCII, 2, latitudeRef.code shl 24)
		tiff.entry(0x0002, RATIONAL, 3, 130)
		tiff.entry(0x0003, ASCII, 2, longitudeRef.code shl 24)
		tiff.entry(0x0004, RATIONAL, 3, 154)
		tiff.putInt(0)
		tiff.putDegrees(latitude)
		tiff.putDegrees(longitude)
		check(!tiff.hasRemaining())

		val header = "Exif\u0000\u0000".toByteArray()
		return ByteBuffer.allocate(4 + header.size + tiff.capacity())
			.putShort(0xFFE1.toShort())
			.putShort((2 + header.size + tiff.capacity()).toShort())
			.put(header)
			.put(tiff.array())
			.array()
	}

	private fun ByteBuffer.entry(tag: Int, type: Int, count: Int, value: Int){
		putShort(tag.toShort()).putShort(type.toShort()).putInt(count).putInt(value)
	}

	private fun ByteBuffer.putDegrees(value: IntArray){
		putInt(value[0]).putInt(1)
		putInt(value[1]).putInt(1)
		putInt(value[2]).putInt(100)
	}

	private companion object{
		const val ASCII = 2
		const val LONG = 4
		const val RATIONAL = 5
	}
}
