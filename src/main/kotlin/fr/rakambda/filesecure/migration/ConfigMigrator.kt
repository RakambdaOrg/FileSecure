package fr.rakambda.filesecure.migration

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import fr.rakambda.filesecure.config.Configuration
import java.util.Locale
import java.util.regex.Pattern

/**
 * Converts FileSecure 12 and MediaConverter configurations into the FileSecure 13 format.
 *
 * Warnings collect everything that couldn't be converted exactly, they should be reviewed before using the result.
 */
class ConfigMigrator{
	private val nodes = JsonNodeFactory.instance
	private val _warnings = mutableListOf<String>()
	val warnings: List<String>
		get() = _warnings

	/**
	 * @param sources Old configurations, each being a FileSecure 12 (`rules`) or MediaConverter (`conversions`) configuration.
	 * @return The new configuration, already validated by the FileSecure 13 parser.
	 */
	fun migrate(sources: List<Source>): ObjectNode{
		val pipelines = nodes.arrayNode()
		for(source in sources){
			val root = source.content
			val known = setOf("rules", "conversions")
			if(root.fieldNames().asSequence().none { it in known }){
				throw IllegalArgumentException("${source.name} is neither a FileSecure (rules) nor a MediaConverter (conversions) configuration")
			}
			warnUnknown(root, known, source.name)
			root.path("rules").forEachIndexed { index, rule -> pipelines.add(migrateRule(rule, "${source.name} rule #${index + 1}")) }
			root.path("conversions").forEachIndexed { index, conversion -> pipelines.add(migrateConversion(conversion, "${source.name} conversion #${index + 1}")) }
		}
		val result = nodes.objectNode().set<ObjectNode>("pipelines", pipelines)
		Configuration.parse(MAPPER.writeValueAsString(result))
		return result
	}

	private fun migrateRule(rule: JsonNode, name: String): ObjectNode{
		warnUnknown(rule, RULE_KEYS, name)
		val pipeline = nodes.objectNode()
		pipeline.put("name", name)
		pipeline.set<JsonNode>("mappings", migrateMappings(rule.path("mappings"), name))

		val scan = nodes.objectNode()
		rule.get("maxDepth")?.let { scan.set<JsonNode>("maxDepth", it) }
		rule.get("folderExcludes")?.takeIf { it.size() > 0 }?.let { scan.set<JsonNode>("excludedFolders", it) }
		if(!scan.isEmpty){
			pipeline.set<JsonNode>("scan", scan)
		}

		val stages = nodes.arrayNode()
		val includes = rule.path("filters")
		val excludes = rule.path("excludes")
		if(includes.size() > 0 || excludes.size() > 0){
			val filter = stages.addObject().put("type", "NameFilter")
			if(includes.size() > 0) filter.set<JsonNode>("includes", includes)
			if(excludes.size() > 0) filter.set<JsonNode>("excludes", excludes)
		}
		stages.addAll(migrateTransformers(rule.path("fileTransformers"), FILE_TRANSFORMERS, name, "file transformer"))
		if(stages.size() > 0){
			pipeline.set<JsonNode>("stages", stages)
		}

		val operation = rule.path("operation").asText("NONE").uppercase(Locale.ROOT)
		if(operation !in OPERATIONS){
			throw IllegalArgumentException("$name: unknown operation '$operation'")
		}
		pipeline.put("operation", operation)
		if(operation == "NONE"){
			_warnings += "$name: operation is NONE (the default), this pipeline only logs what it would do"
		}
		if(rule.path("skipIfAlreadyExists").asBoolean(false)){
			pipeline.put("conflict", "SKIP")
		}
		if(rule.path("setFileCreationDate").asBoolean(false)){
			pipeline.put("setFileCreationDate", true)
		}

		val folderActions = migrateTransformers(rule.path("folderTransformers"), FOLDER_TRANSFORMERS, name, "folder transformer")
		if(folderActions.size() > 0){
			pipeline.set<JsonNode>("folderActions", folderActions)
		}
		if(folderActions.any { it.path("type").asText() == "DeleteIfEmpty" && it.path("depth").asInt(1) <= 1 }){
			_warnings += "$name: DeleteIfEmpty used to delete the input folder itself once empty, it now keeps it"
		}
		return pipeline
	}

	/**
	 * Converts transformers into stages or folder actions, sorted by their old priority.
	 * The old version stored them in an unordered set, the priority was meant to be their order but was never applied.
	 */
	private fun migrateTransformers(transformers: JsonNode, definitions: Map<String, TransformerDefinition>, name: String, kind: String): ArrayNode{
		val migrated = transformers.mapNotNull { transformer ->
			val oldType = transformer.path("name").asText()
			val definition = definitions[oldType]
			if(definition == null){
				_warnings += "$name: unknown $kind '$oldType' dropped"
				return@mapNotNull null
			}
			warnUnknown(transformer, definition.parameters + "name", "$name $oldType")
			val node = nodes.objectNode().put("type", definition.newType)
			definition.parameters.forEach { parameter -> transformer.get(parameter)?.let { node.set<JsonNode>(parameter, it) } }
			definition.priority to node
		}
		if(migrated.size > 1){
			_warnings += "$name: ${kind}s were applied in an unspecified order, they are now ordered by their intended priority: " +
					migrated.sortedBy { it.first }.joinToString(", ") { it.second.path("type").asText() } + ". Check this order is the one you expect"
		}
		return nodes.arrayNode().addAll(migrated.sortedBy { it.first }.map { it.second })
	}

	private fun migrateConversion(conversion: JsonNode, name: String): ObjectNode{
		warnUnknown(conversion, CONVERSION_KEYS, name)
		val pipeline = nodes.objectNode()
		pipeline.put("name", name)
		conversion.get("enabled")?.let { pipeline.set<JsonNode>("enabled", it) }
		pipeline.set<JsonNode>("mappings", nodes.arrayNode().add(migrateMapping(conversion, name)))

		val scan = pipeline.putObject("scan").put("skipHidden", true)
		val excluded = conversion.path("excluded")
		if(excluded.size() > 0){
			val patterns = scan.putArray("excludedFolders")
			excluded.forEach { patterns.add(Pattern.quote(it.asText().replace("\\", "/"))) }
		}

		val convert = pipeline.putArray("stages").addObject().put("type", "Convert")
		conversion.path("processors").takeIf { it.size() > 0 }?.let { processors ->
			val array = convert.putArray("processors")
			processors.forEach { array.add(it.asText().uppercase(Locale.ROOT)) }
		}
		conversion.path("extensions").takeIf { it.size() > 0 }?.let { convert.set<JsonNode>("extensions", it) }
		conversion.path("filters").takeIf { it.size() > 0 }?.let { filters ->
			val array = convert.putArray("filters")
			filters.forEach { filter ->
				warnUnknown(filter, setOf("name", "value"), "$name filter")
				array.addObject()
					.put("type", filter.path("name").asText())
					.set<JsonNode>("value", filter.path("value"))
			}
		}
		convert.put("unhandled", "SKIP")
		conversion.get("database")?.let { convert.set<JsonNode>("cache", it) }

		// The output was never overwritten: kept as is when copying, and the conversion failed when moving
		val deleteInput = conversion.path("deleteInput").asBoolean(true)
		pipeline.put("operation", if(deleteInput) "MOVE" else "COPY")
		pipeline.put("conflict", "SKIP")
		conversion.get("temp")?.let { pipeline.set<JsonNode>("temp", it) }
		return pipeline
	}

	private fun migrateMappings(mappings: JsonNode, name: String): ArrayNode{
		if(mappings.size() == 0){
			throw IllegalArgumentException("$name has no mappings")
		}
		return nodes.arrayNode().addAll(mappings.map { migrateMapping(it, name) })
	}

	private fun migrateMapping(mapping: JsonNode, name: String): ObjectNode{
		val input = mapping.get("input")?.asText() ?: throw IllegalArgumentException("$name has no input")
		val output = mapping.get("output")?.asText() ?: throw IllegalArgumentException("$name has no output")
		return nodes.objectNode().put("input", input).put("output", output)
	}

	private fun warnUnknown(node: JsonNode, known: Set<String>, name: String){
		node.fieldNames().asSequence()
			.filter { it !in known }
			.forEach { _warnings += "$name: unknown key '$it' was ignored by the previous version, it is dropped" }
	}

	data class Source(val name: String, val content: JsonNode)

	private data class TransformerDefinition(val newType: String, val priority: Int, val parameters: Set<String>)

	companion object{
		val MAPPER: JsonMapper = JsonMapper.builder()
			.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
			.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
			.build()

		private val OPERATIONS = setOf("COPY", "MOVE", "MOVE_WITH_COPY", "NONE")
		private val RULE_KEYS = setOf("operation", "mappings", "filters", "excludes", "folderExcludes", "fileTransformers", "folderTransformers", "skipIfAlreadyExists", "setFileCreationDate", "maxDepth")
		private val CONVERSION_KEYS = setOf("input", "output", "database", "temp", "deleteInput", "enabled", "excluded", "processors", "extensions", "filters")

		// Priorities of the old transformers: lower ran first. IfOlderThan shared 0 with renames, being a filter it is put first
		private val FILE_TRANSFORMERS = mapOf(
			"IfOlderThanTransformer" to TransformerDefinition("IfOlderThan", -1, setOf("dayOffset", "minuteOffset")),
			"RenameWithDateTransformer" to TransformerDefinition("RenameWithDate", 0, setOf("format", "locale", "filters", "zone")),
			"RenameWithDatePatternTransformer" to TransformerDefinition("RenameWithDatePattern", 0, setOf("outputFormat", "regex", "dateFormat", "timeFormat", "locale", "zone")),
			"FolderUniquePerDayTransformer" to TransformerDefinition("FolderUniquePerDay", 10, emptySet()),
			"FolderPerDayTransformer" to TransformerDefinition("FolderPerDay", 11, emptySet()),
			"FolderPerWeekTransformer" to TransformerDefinition("FolderPerWeek", 12, emptySet()),
			"FolderCanonicalTransformer" to TransformerDefinition("FolderCanonical", 12, setOf("elementCount")),
			"FolderPerMonthTransformer" to TransformerDefinition("FolderPerMonth", 13, emptySet()),
			"FolderPerYearTransformer" to TransformerDefinition("FolderPerYear", 14, emptySet()),
			"LowercaseTransformer" to TransformerDefinition("Lowercase", Int.MAX_VALUE, emptySet()),
			"NoRenameTransformer" to TransformerDefinition("NoRename", Int.MAX_VALUE, emptySet()),
		)
		private val FOLDER_TRANSFORMERS = mapOf(
			"DeleteChildrenTransformer" to TransformerDefinition("DeleteChildren", 10, setOf("names")),
			"DeleteIfEmptyTransformer" to TransformerDefinition("DeleteIfEmpty", Int.MAX_VALUE, setOf("depth")),
			"DeleteIfOlderThanTransformer" to TransformerDefinition("DeleteIfOlderThan", Int.MAX_VALUE, setOf("dayOffset", "minuteOffset", "depth")),
		)
	}
}
