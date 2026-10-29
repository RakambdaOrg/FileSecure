package fr.rakambda.filesecure.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

internal const val MAPPING_TABLE = "mappingTable"
internal const val MAPPINGS_FILE = "mappingsFile"
private const val MAPPINGS = "mappings"
internal val TABLE_KEYS = setOf("input", "output", "paths", "entries")
internal val RULE_KEYS = setOf("regex", "replacement")
private val DRIVE = Regex("[A-Za-z]:.*")

/**
 * Replaces the `mappingTable` of every pipeline with regular mappings, appended after the pipeline's own: each entry `key: value` becomes
 * `{input: <input>/<key>, output: <output>/<value>}`, keys and values being first rewritten by the first `paths` rule fully matching them.
 *
 * @throws InvalidConfigurationException If a `mappingTable` is invalid.
 */
internal fun expandMappingTables(tree: ConfigurationTree) = forEachPipeline(tree) { pipeline, name ->
	val table = pipeline.remove(MAPPING_TABLE) ?: return@forEachPipeline
	val generated = MappingTable.read(table, name).expand()
	val mappings = ownMappings(pipeline, name, MAPPING_TABLE)
	generated.forEach { (input, output) -> mappings.addObject().put("input", input).put("output", output) }
}

/**
 * Appends to the mappings of every pipeline the list of mappings read from its `mappingsFile` (YAML or JSON, an empty file meaning no mapping), so a
 * tool can rewrite that file without touching the main configuration. A relative path is resolved against the working directory.
 *
 * @throws InvalidConfigurationException If a `mappingsFile` is invalid or can't be read.
 */
internal fun includeMappingsFiles(tree: ConfigurationTree) = forEachPipeline(tree) { pipeline, name ->
	val node = pipeline.remove(MAPPINGS_FILE) ?: return@forEachPipeline
	fun error(message: String) = InvalidConfigurationException("'$MAPPINGS_FILE' of pipeline '$name' $message")
	val path = node.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotEmpty() } ?: throw error("must be a non-empty text")
	val included = try{
		Files.newBufferedReader(Path.of(path)).use { ConfigurationTree.readYaml(it).root }
	}
	catch(e: IOException){
		throw error("can't be read: $e")
	}
	when(included){
		is MissingNode, is NullNode -> {}
		is ArrayNode -> ownMappings(pipeline, name, MAPPINGS_FILE).addAll(included)
		else -> throw error("must contain a list of mappings: $path")
	}
}

private fun forEachPipeline(tree: ConfigurationTree, action: (ObjectNode, String) -> Unit){
	val root = tree.root as? ObjectNode ?: return
	(root.get("pipelines") as? ArrayNode)?.forEachIndexed { index, pipeline ->
		if(pipeline is ObjectNode){
			action(pipeline, pipeline.get("name")?.takeIf { it.isTextual }?.asText() ?: "#${index + 1}")
		}
	}
}

private fun ownMappings(pipeline: ObjectNode, name: String, combinedWith: String): ArrayNode = when(val own = pipeline.get(MAPPINGS)){
	null, is NullNode -> pipeline.putArray(MAPPINGS)
	is ArrayNode -> own
	else -> throw InvalidConfigurationException("'$MAPPINGS' of pipeline '$name' must be a list to be combined with '$combinedWith'")
}

private class MappingTable(
	private val pipeline: String,
	private val input: String,
	private val output: String,
	private val rules: List<Pair<Pattern, String>>,
	private val entries: List<Pair<String, String>>,
){
	fun expand(): List<Pair<String, String>> = entries.map { (key, value) ->
		join(input, rewrite(key)) to join(output, rewrite(value))
	}

	private fun rewrite(path: String): String{
		val result = rules.firstNotNullOfOrNull { (regex, replacement) ->
			val matcher = regex.matcher(path)
			if(!matcher.matches()){
				return@firstNotNullOfOrNull null
			}
			try{
				matcher.replaceFirst(replacement)
			}
			catch(e: RuntimeException){
				throw tableError(pipeline, "has an invalid replacement '$replacement' for '$path': ${e.message}")
			}
		} ?: return path
		checkRelative(result, "rewritten path '$result' (from '$path')")
		return result
	}

	private fun join(base: String, path: String): String = base.trimEnd('/', '\\') + "/" + path

	private fun checkRelative(path: String, label: String){
		when{
			path.isEmpty() -> throw tableError(pipeline, "has an empty entry")
			path.startsWith('/') || path.startsWith('\\') || DRIVE.matches(path) -> throw tableError(pipeline, "must have relative entries: $label is absolute")
			path.split('/', '\\').any { it == ".." } -> throw tableError(pipeline, "must have entries without '..': $label")
		}
	}

	companion object{
		fun read(node: JsonNode, pipeline: String): MappingTable{
			fun error(message: String) = tableError(pipeline, message)
			fun checkKeys(node: ObjectNode, allowed: Set<String>, label: String){
				node.properties().map { it.key }.firstOrNull { it !in allowed }?.let {
					throw error("has an unknown key '$it'$label, expected one of ${allowed.joinToString()}")
				}
			}
			fun text(node: JsonNode?, label: String): String = node?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotEmpty() }
				?: throw error(if(node == null || node.isNull) "has no '$label'" else "must have a non-empty text as '$label'")

			if(node !is ObjectNode){
				throw error("must be a map with keys ${TABLE_KEYS.joinToString()}")
			}
			checkKeys(node, TABLE_KEYS, "")
			val input = text(node.get("input"), "input")
			val output = text(node.get("output"), "output")
			val rules = when(val paths = node.get("paths")){
				null, is NullNode -> emptyList()
				is ArrayNode -> paths.mapIndexed { index, rule ->
					val label = " in rule #${index + 1} of 'paths'"
					if(rule !is ObjectNode){
						throw error("must have maps with keys regex, replacement in 'paths'")
					}
					checkKeys(rule, RULE_KEYS, label)
					val regex = text(rule.get("regex"), "regex")
					val pattern = try{
						Pattern.compile(regex)
					}
					catch(e: PatternSyntaxException){
						throw error("has an invalid regex '$regex'$label: ${e.description}")
					}
					val replacement = rule.get("replacement")?.takeIf { it.isTextual }?.asText() ?: throw error("has no text 'replacement'$label")
					pattern to replacement
				}
				else -> throw error("must have a list as 'paths'")
			}
			val entries = node.get("entries") as? ObjectNode ?: throw error(if(node.get("entries").let { it == null || it.isNull }) "has no 'entries'" else "must have a map as 'entries'")
			val table = MappingTable(pipeline, input, output, rules, entries.properties().map { (key, value) ->
				if(!value.isTextual){
					throw error("must have a text value for entry '$key', quote it")
				}
				key to value.asText()
			})
			table.entries.forEach { (key, value) ->
				table.checkRelative(key, "key '$key'")
				table.checkRelative(value, "value '$value' of '$key'")
			}
			return table
		}
	}
}

private fun tableError(pipeline: String, message: String) = InvalidConfigurationException("'$MAPPING_TABLE' of pipeline '$pipeline' $message")
