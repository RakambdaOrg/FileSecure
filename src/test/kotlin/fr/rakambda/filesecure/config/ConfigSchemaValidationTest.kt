package fr.rakambda.filesecure.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * Validates configurations against `config.schema.json` as an editor would: anchors and merges resolved, before `defaults` and `mappingTable` are
 * applied.
 */
class ConfigSchemaValidationTest{
	@Test
	fun acceptsReadmeExamples(){
		val blocks = README_BLOCK.findAll(Path.of("README.md").readText()).map { it.groupValues[1] }.toList()
		assertThat(blocks).hasSizeGreaterThanOrEqualTo(5)
		blocks.forEach { block ->
			val tree = readTree(block).let { if(it.properties().all { (key, _) -> key in TOP_LEVEL_KEYS }) it else wrapInPipelines(it) }
			assertThat(errors(tree)).describedAs(block).isEmpty()
			assertThatCode { Configuration.parse(tree.toString(), json = true) }.describedAs(block).doesNotThrowAnyException()
		}
	}

	@Test
	fun acceptsConfigurationsOfTests(){
		val samples = Files.walk(Path.of("src/test/kotlin")).use { files ->
			files.filter { it.extension == "kt" && it.fileName.toString() != "ConfigSchemaValidationTest.kt" }.toList()
		}.flatMap { file -> PARSED_SAMPLE.findAll(file.readText()).map { it.groupValues[1].replace("\${'$'}", "$").trimIndent() } }
			.filter { runCatching { Configuration.parse(it) }.isSuccess }
		assertThat(samples).hasSizeGreaterThanOrEqualTo(15)
		samples.forEach { sample ->
			assertThat(errors(canonicalEnums(readTree(sample)))).describedAs(sample).isEmpty()
		}
	}

	@Test
	fun flagsLowercaseEnumValuesAcceptedByFileSecure(){
		val content = "pipelines:\n  - mappings: [{input: /a, output: /b}]\n    operation: move"

		assertThat(Configuration.parse(content).pipelines.single().operation).isEqualTo(Operation.MOVE)
		assertThat(errors(readTree(content))).isNotEmpty()
	}

	@Test
	fun rejectsInvalidConfigurations(){
		val pipeline = "pipelines:\n  - mappings: [{input: /a, output: /b}]\n"
		listOf(
			"pipeline: []",
			"useDefaults: false\n$pipeline",
			"${pipeline}    depth: 2",
			"${pipeline}    templates: {a: b}",
			"${pipeline}    scan: {useDefaults: false}",
			"${pipeline}    scan: {maxDepth: -1}",
			"${pipeline}    operation: DELETE",
			"${pipeline}    conflict: OVERWRITE",
			"${pipeline}    enabled: maybe",
			"pipelines:\n  - mappings: [{input: /a}]",
			"pipelines:\n  - mappings: [{input: /a, output: /b, depth: 1}]",
			"${pipeline}    stages: [{type: NameFilter, exclude: [a]}]",
			"${pipeline}    stages: [{type: FolderPerCentury}]",
			"${pipeline}    stages: [{exclude: [a]}]",
			"${pipeline}    stages: [{type: FolderPerYear, depth: 1}]",
			"${pipeline}    stages: [{type: FolderCanonical}]",
			"${pipeline}    stages: [{type: IfOlderThan, dayOffset: two}]",
			"${pipeline}    stages: [{type: RegexPath}]",
			"${pipeline}    stages: [{type: RegexPath, rules: [{regex: a}]}]",
			"${pipeline}    stages: [{type: RegexPath, rules: [{regex: a, replacement: b}], unmatched: IGNORE}]",
			"${pipeline}    stages: [{type: RenameWithDatePattern, regex: '(?<date>.*) (?<time>.*)', dateFormat: yyyy}]",
			"${pipeline}    stages: [{type: Convert, processors: [VIDEO_TO_H264]}]",
			"${pipeline}    stages: [{type: Convert, unhandled: KEEP}]",
			"${pipeline}    stages: [{type: Convert, filters: [{type: LengthMoreThan}]}]",
			"${pipeline}    stages: [{type: Convert, filters: [{type: SizeMoreThan, value: 2}]}]",
			"${pipeline}    folderActions: [{type: DeleteIfEmpty, days: 3}]",
			"${pipeline}    folderActions: [{type: DeleteEverything}]",
			"pipelines:\n  - mappingTable: {input: /a, output: /b}",
			"pipelines:\n  - mappingTable: {input: /a, output: /b, entries: {a: b}, path: []}",
			"pipelines:\n  - mappingTable: {input: /a, output: /b, entries: {a: 12}}",
			"pipelines:\n  - mappingTable: {input: /a, output: /b, entries: {a: b}, paths: [{regex: a}]}",
			"defaults: {name: x}\npipelines: []",
			"defaults: {mappings: []}\npipelines: []",
			"defaults: {enabled: false}\npipelines: []",
			"defaults: {useDefaults: false}\npipelines: []",
			"defaults: {operaton: MOVE}\npipelines: []",
		).forEach { content ->
			assertThat(errors(readTree(content))).describedAs(content).isNotEmpty()
			assertThatThrownBy { Configuration.parse(content) }.describedAs("FileSecure also rejects $content").isInstanceOf(Exception::class.java)
		}
	}

	companion object{
		private val README_BLOCK = Regex("```yaml\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
		private val PARSED_SAMPLE = Regex("Configuration\\.parse\\(\"\"\"(.*?)\"\"\"", RegexOption.DOT_MATCHES_ALL)
		private val TOP_LEVEL_KEYS = setOf("pipelines", "defaults", "templates")
		private val ENUM_KEYS = setOf("operation", "conflict", "unhandled", "unmatched", "processors")
		private val schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
			.getSchema(ConfigSchemaTest.SCHEMA_PATH.readText(), InputFormat.JSON)

		/**
		 * The YAML tree as FileSecure reads it, anchors and `<<` merges being resolved.
		 */
		fun readTree(content: String): ObjectNode = ConfigurationTree.readYaml(content.reader()).root as ObjectNode

		fun errors(tree: JsonNode): List<String> = schema.validate(tree.toString(), InputFormat.JSON).map { it.toString() }

		private fun wrapInPipelines(pipeline: JsonNode): ObjectNode = JsonNodeFactory.instance.objectNode().apply { putArray("pipelines").add(pipeline) }

		/**
		 * Upper-cases enum values, which FileSecure reads case-insensitively.
		 */
		private fun canonicalEnums(node: JsonNode): JsonNode{
			when(node){
				is ObjectNode -> node.properties().forEach { (key, value) ->
					when{
						key !in ENUM_KEYS -> canonicalEnums(value)
						value.isTextual -> node.put(key, value.asText().uppercase(Locale.ROOT))
						value is ArrayNode -> value.forEachIndexed { index, item -> value.set(index, TextNode(item.asText().uppercase(Locale.ROOT))) }
					}
				}
				is ArrayNode -> node.forEach { canonicalEnums(it) }
			}
			return node
		}
	}
}
