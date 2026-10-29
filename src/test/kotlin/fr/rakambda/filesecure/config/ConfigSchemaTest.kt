package fr.rakambda.filesecure.config

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fr.rakambda.filesecure.conversion.ProbeFilter
import fr.rakambda.filesecure.conversion.ProcessorType
import fr.rakambda.filesecure.folder.FolderAction
import fr.rakambda.filesecure.stage.RegexPath
import fr.rakambda.filesecure.stage.Stage
import fr.rakambda.filesecure.stage.UnhandledStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Checks that `config.schema.json` describes exactly what the configuration classes accept.
 */
class ConfigSchemaTest{
	private val schema = ObjectMapper().readTree(SCHEMA_PATH.toFile())

	@Test
	fun usesDraft07(){
		assertThat(schema["\$schema"].asText()).isEqualTo("http://json-schema.org/draft-07/schema#")
	}

	@Test
	fun describesTopLevelKeys(){
		assertStrictObject(schema, parameters(Configuration::class) + listOf("defaults", "templates"))
	}

	@Test
	fun describesPipelineKeys(){
		assertStrictObject(definition("pipeline"), parameters(Pipeline::class) + listOf(USE_DEFAULTS, MAPPING_TABLE, MAPPINGS_FILE))
		assertThat(required(definition("pipeline"))).isEmpty()
	}

	@Test
	fun describesDefaultsKeys(){
		val defaults = definition("defaults")
		assertStrictObject(defaults, parameters(Pipeline::class) + listOf(USE_DEFAULTS, MAPPING_TABLE, MAPPINGS_FILE) - FORBIDDEN_KEYS.toSet())
		properties(defaults).forEach { (key, value) ->
			assertThat(value["\$ref"].asText()).describedAs(key).isEqualTo("#/definitions/pipeline/properties/$key")
		}
	}

	@Test
	fun describesNestedObjects(){
		assertClass(definition("mapping"), Mapping::class)
		assertClass(definition("scan"), ScanOptions::class)
		assertClass(definition("regexRule"), RegexPath.Rule::class)
		assertStrictObject(definition("mappingTable"), TABLE_KEYS.toList())
		assertThat(required(definition("mappingTable"))).containsExactlyInAnyOrder("input", "output", "entries")
		assertThat(RULE_KEYS).isEqualTo(parameters(RegexPath.Rule::class).toSet())
		assertThat(resolve(definition("mappingTable")["properties"]["paths"]["items"])).isSameAs(definition("regexRule"))
	}

	@ParameterizedTest
	@MethodSource("polymorphicTypes")
	fun describesEveryType(base: KClass<*>, name: String){
		val subTypes = base.findAnnotation<JsonSubTypes>()!!.value.associate { it.name to it.value }
		val definition = definition(name)
		assertThat(enumValues(definition["properties"]["type"])).containsExactlyInAnyOrderElementsOf(subTypes.keys)
		assertThat(required(definition)).containsExactly("type")

		val variants = definition["oneOf"].map(::resolve).associateBy { it["properties"]["type"]["const"].asText() }
		assertThat(variants.keys).containsExactlyInAnyOrderElementsOf(subTypes.keys)
		subTypes.forEach { (type, kClass) ->
			val variant = variants.getValue(type)
			assertClass(variant, kClass, "type")
			assertThat(definition(type)).describedAs("definition named after the type").isSameAs(variant)
		}
	}

	@Test
	fun listsEnumValues(){
		assertThat(enumValues(definition("operation"))).containsExactlyElementsOf(Operation.entries.map { it.name })
		assertThat(enumValues(definition("conflict"))).containsExactlyElementsOf(ConflictStrategy.entries.map { it.name })
		assertThat(enumValues(definition("verify"))).containsExactlyElementsOf(Verification.entries.map { it.name })
		assertThat(definition("verify")["default"].asText()).isEqualTo(Pipeline().verify.name)
		assertThat(enumValues(definition("processor"))).containsExactlyElementsOf(ProcessorType.entries.map { it.name })
		assertThat(resolve(definition("Convert")["properties"]["processors"]["items"])).isSameAs(definition("processor"))
		listOf(definition("Convert")["properties"]["unhandled"], definition("RegexPath")["properties"]["unmatched"]).forEach {
			assertThat(enumValues(it)).containsExactlyElementsOf(UnhandledStrategy.entries.map { value -> value.name })
		}
	}

	@Test
	fun resolvesEveryReference(){
		fun visit(node: JsonNode){
			node["\$ref"]?.let { assertThat(resolve(node)).describedAs(it.asText()).isNotNull() }
			node.forEach(::visit)
		}
		visit(schema)
	}

	private fun definition(name: String): JsonNode = schema["definitions"][name]

	private fun resolve(node: JsonNode): JsonNode = schema.at(node["\$ref"].asText().removePrefix("#"))

	private fun properties(node: JsonNode): Map<String, JsonNode> = node["properties"].properties().associate { it.key to it.value }

	private fun required(node: JsonNode): List<String> = node["required"]?.map { it.asText() } ?: emptyList()

	private fun enumValues(node: JsonNode): List<String> = node["enum"].map { it.asText() }

	private fun assertStrictObject(node: JsonNode, keys: List<String>){
		assertThat(properties(node).keys).containsExactlyInAnyOrderElementsOf(keys)
		assertThat(node["additionalProperties"].booleanValue()).describedAs("additionalProperties").isFalse()
	}

	/**
	 * The object must have exactly the parameters of the class (plus [extra]), and require the mandatory ones.
	 */
	private fun assertClass(node: JsonNode, kClass: KClass<*>, vararg extra: String){
		val parameters = kClass.primaryConstructor!!.parameters
		assertStrictObject(node, parameters.map { it.name!! } + extra)
		assertThat(required(node)).describedAs("required of ${kClass.simpleName}")
			.containsExactlyInAnyOrderElementsOf(parameters.filter { !it.isOptional && !it.type.isMarkedNullable }.map { it.name!! } + extra)
	}

	private fun parameters(kClass: KClass<*>): List<String> = kClass.primaryConstructor!!.parameters.map { it.name!! }

	companion object{
		val SCHEMA_PATH: Path = Path.of("config.schema.json")

		@JvmStatic
		fun polymorphicTypes(): List<Arguments> = listOf(
			Arguments.of(Stage::class, "stage"),
			Arguments.of(FolderAction::class, "folderAction"),
			Arguments.of(ProbeFilter::class, "probeFilter"),
		)
	}
}
