package fr.rakambda.filesecure.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import fr.rakambda.filesecure.stage.Convert
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

data class Configuration(
	val pipelines: List<Pipeline> = emptyList(),
){
	/**
	 * Checks constraints the JSON schema alone can't express.
	 *
	 * @throws InvalidConfigurationException If the configuration can't be run.
	 */
	fun validate(){
		pipelines.forEachIndexed { index, pipeline ->
			val name = pipeline.displayName(index)
			if(pipeline.mappings.isEmpty()){
				throw InvalidConfigurationException("Pipeline '$name' has no mappings")
			}
			if(pipeline.stages.count { it is Convert } > 1){
				throw InvalidConfigurationException("Pipeline '$name' has more than one Convert stage")
			}
			if(pipeline.scan.maxDepth < 0){
				throw InvalidConfigurationException("Pipeline '$name' has a negative maxDepth")
			}
		}
	}

	companion object{
		private val mapper = JsonMapper.builder()
			.addModule(kotlinModule())
			.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
			.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.addModule(SimpleModule().addDeserializer(Path::class.java, PathDeserializer))
			.build()

		fun load(path: Path): Configuration = Files.newBufferedReader(path).use { parse(it) }

		fun parse(reader: Reader): Configuration = mapper.readValue<Configuration>(reader).also { it.validate() }

		fun parse(json: String): Configuration = parse(json.reader())
	}
}

/**
 * Reads paths as plain file paths. Jackson's default reads `D:/folder` as a URI with the scheme `D` when not running on Windows.
 */
private object PathDeserializer : StdScalarDeserializer<Path>(Path::class.java){
	private fun readResolve(): Any = PathDeserializer
	
	override fun deserialize(parser: JsonParser, context: DeserializationContext): Path = Path.of(parser.valueAsString)
}

class InvalidConfigurationException(message: String) : RuntimeException(message)
