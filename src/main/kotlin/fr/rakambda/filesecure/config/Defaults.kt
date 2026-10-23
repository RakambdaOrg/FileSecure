package fr.rakambda.filesecure.config

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

private const val DEFAULTS = "defaults"
private const val USE_DEFAULTS = "useDefaults"
private val FORBIDDEN_KEYS = listOf("name", "mappings", MAPPING_TABLE, "enabled", USE_DEFAULTS)
private val LIST_KEYS = setOf("stages", "folderActions")
private const val SCAN = "scan"

/**
 * Removes the `defaults` section of the configuration and merges it into every pipeline not opting out with `useDefaults: false`:
 * its `stages` and `folderActions` are placed before the pipeline's own, `scan` is merged key by key and other keys only apply when the pipeline
 * doesn't set them.
 *
 * @throws InvalidConfigurationException If `defaults` or `useDefaults` is misused.
 * @throws JsonMappingException If `defaults` isn't a valid pipeline.
 */
internal fun applyDefaults(tree: ConfigurationTree, mapper: ObjectMapper){
	val root = tree.root as? ObjectNode ?: return
	val defaults = root.remove(DEFAULTS)?.takeUnless { it.isNull }
	if(defaults != null){
		validateDefaults(defaults, tree, mapper)
	}
	(root.get("pipelines") as? ArrayNode)?.forEachIndexed { index, pipeline ->
		if(pipeline !is ObjectNode){
			return@forEachIndexed
		}
		val useDefaults = pipeline.remove(USE_DEFAULTS)
		if(useDefaults != null && !useDefaults.isBoolean){
			throw InvalidConfigurationException("'$USE_DEFAULTS' of pipeline #${index + 1} must be true or false")
		}
		if(defaults != null && useDefaults?.booleanValue() != false){
			merge(defaults as ObjectNode, pipeline)
		}
	}
}

private fun validateDefaults(defaults: JsonNode, tree: ConfigurationTree, mapper: ObjectMapper){
	if(defaults !is ObjectNode){
		throw InvalidConfigurationException("'$DEFAULTS' must be a map of pipeline keys")
	}
	FORBIDDEN_KEYS.firstOrNull { defaults.has(it) }?.let {
		throw InvalidConfigurationException("'$it' can't be set in '$DEFAULTS'")
	}
	try{
		tree.bind(mapper, defaults, Pipeline::class.java)
	}
	catch(e: JsonMappingException){
		throw JsonMappingException.wrapWithPath(e, Configuration::class.java, DEFAULTS)
	}
}

/**
 * Default nodes are shared between pipelines rather than copied, so they keep their location. They are never modified afterwards.
 */
private fun merge(defaults: ObjectNode, pipeline: ObjectNode){
	defaults.properties().forEach { (key, value) ->
		val own = pipeline.get(key)
		when{
			own == null -> pipeline.set<JsonNode>(key, value)
			key in LIST_KEYS && own is ArrayNode && value is ArrayNode -> pipeline.set<JsonNode>(key, pipeline.arrayNode().addAll(value).addAll(own))
			key == SCAN && own is ObjectNode && value is ObjectNode -> pipeline.set<JsonNode>(key, pipeline.objectNode().setAll<ObjectNode>(value).setAll<JsonNode>(own))
		}
	}
}
