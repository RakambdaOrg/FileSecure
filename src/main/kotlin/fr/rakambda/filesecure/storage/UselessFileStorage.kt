package fr.rakambda.filesecure.storage

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val log = KotlinLogging.logger {}

/**
 * Remembers files that don't need to be converted, to avoid probing them again on the next runs.
 */
interface UselessFileStorage : AutoCloseable{
	/**
	 * @return true if the file was marked as useless and hasn't been modified since.
	 */
	fun isUseless(path: Path, lastModified: Timestamp): Boolean

	fun setUseless(path: Path, lastModified: Timestamp)

	/**
	 * Persists the files marked since the last save.
	 */
	fun save()

	override fun close() = save()
}

object NoOpStorage : UselessFileStorage{
	override fun isUseless(path: Path, lastModified: Timestamp) = false

	override fun setUseless(path: Path, lastModified: Timestamp){
	}

	override fun save(){
	}
}

/**
 * H2 database storage, compatible with MediaConverter's database files.
 */
class H2Storage(private val databaseFile: Path) : UselessFileStorage{
	private val useless = ConcurrentHashMap<String, Timestamp?>()
	private val newUseless = ConcurrentLinkedQueue<Pair<String, Timestamp>>()
	private val saveLock = ReentrantLock()

	init{
		log.debug { "Loading useless files from $databaseFile" }
		databaseFile.toAbsolutePath().parent?.let(Files::createDirectories)
		connect().use { connection ->
			connection.createStatement().use { statement ->
				statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS Useless(
						Filee VARCHAR(512) NOT NULL,
						LastModified TIMESTAMP,
						PRIMARY KEY(Filee)
					)""".trimIndent())
				statement.executeUpdate("ALTER TABLE Useless ADD COLUMN IF NOT EXISTS LastModified TIMESTAMP")
				statement.executeQuery("SELECT Filee, LastModified FROM Useless").use { result ->
					while(result.next()){
						useless[result.getString("Filee")] = result.getTimestamp("LastModified")
					}
				}
			}
		}
		log.info { "Loaded ${useless.size} useless files from $databaseFile" }
	}

	override fun isUseless(path: Path, lastModified: Timestamp): Boolean{
		val key = key(path)
		if(!useless.containsKey(key)){
			return false
		}
		val entryLastModified = useless[key] ?: return true
		return entryLastModified >= lastModified
	}

	override fun setUseless(path: Path, lastModified: Timestamp){
		log.debug { "Marking $path as useless" }
		val key = key(path)
		useless[key] = lastModified
		newUseless.add(key to lastModified)
		if(newUseless.size >= SAVE_THRESHOLD){
			save()
		}
	}

	override fun save(): Unit = saveLock.withLock {
		if(newUseless.isEmpty()){
			return
		}
		connect().use { connection ->
			connection.prepareStatement("MERGE INTO Useless(Filee, LastModified) VALUES(?, ?)").use { statement ->
				var count = 0
				while(true){
					val (path, lastModified) = newUseless.poll() ?: break
					statement.setString(1, path)
					statement.setTimestamp(2, lastModified)
					statement.addBatch()
					count++
				}
				statement.executeBatch()
				log.debug { "Saved $count new useless files" }
			}
		}
	}

	private fun connect(): Connection = DriverManager.getConnection("jdbc:h2:${databaseFile.toAbsolutePath()}")

	private fun key(path: Path) = path.toString().replace("\\", "/")

	private companion object{
		const val SAVE_THRESHOLD = 100
	}
}
