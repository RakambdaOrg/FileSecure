package fr.rakambda.filesecure.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.MapperBuilder
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
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
		private const val TEMPLATES = "templates"
		private val yamlMapper = configure(YAMLMapper.builder()).build()
		private val jsonMapper = configure(JsonMapper.builder())
			.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
			.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
			.build()
		
		private fun <M : ObjectMapper, B : MapperBuilder<M, B>> configure(builder: B): B = builder
			.addModule(kotlinModule())
			.addModule(SimpleModule().addDeserializer(Path::class.java, PathDeserializer))
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		
		/**
		 * Reads a YAML configuration, or a JSON one if the file name ends with `.json`.
		 */
		fun load(path: Path): Configuration = Files.newBufferedReader(path).use {
			parse(it, json = path.fileName.toString().endsWith(".json", ignoreCase = true))
		}
		
		/**
		 * Reads a configuration: YAML anchors and aliases are resolved, `templates` is dropped, `defaults` is merged into the pipelines and
		 * `mappingTable`s are expanded into mappings before binding.
		 */
		fun parse(reader: Reader, json: Boolean = false): Configuration{
			val mapper = if(json) jsonMapper else yamlMapper
			val tree = if(json) ConfigurationTree(mapper.readTree(reader)) else ConfigurationTree.readYaml(reader)
			(tree.root as? ObjectNode)?.remove(TEMPLATES)
			applyDefaults(tree, mapper)
			expandMappingTables(tree)
			return tree.bind(mapper, tree.root, Configuration::class.java).also { it.validate() }
		}
		
		fun parse(content: String, json: Boolean = false): Configuration = parse(content.reader(), json)
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
