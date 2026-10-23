package fr.rakambda.filesecure.config

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.io.ContentReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import java.io.Reader
import java.math.BigInteger
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A configuration tree remembering where its nodes were read, so binding errors can tell the line of the faulty value.
 */
internal class ConfigurationTree(
	val root: JsonNode,
	private val locations: Map<JsonNode, JsonLocation> = emptyMap(),
){
	fun <T> bind(mapper: ObjectMapper, node: JsonNode, type: Class<T>): T = mapper.readValue(LocatingParser(node, mapper), type)

	private inner class LocatingParser(node: JsonNode, mapper: ObjectMapper) : TreeTraversingParser(node, mapper){
		override fun currentTokenLocation(): JsonLocation = currentNode()?.let { locations[it] } ?: JsonLocation.NA

		override fun currentLocation(): JsonLocation = currentTokenLocation()

		@Deprecated("Deprecated in Jackson")
		override fun getTokenLocation(): JsonLocation = currentTokenLocation()

		@Deprecated("Deprecated in Jackson")
		override fun getCurrentLocation(): JsonLocation = currentTokenLocation()
	}

	companion object{
		private val INTEGER = Regex("[-+]?(0|[1-9][0-9]*)")
		private val DECIMAL = Regex("[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?")
		private val BOOLEAN = Regex("true|True|TRUE|false|False|FALSE")

		/**
		 * Reads a YAML document, resolving anchors, aliases and `<<` merge keys. Scalars are typed like Jackson's YAML parser does: only `true` and
		 * `false` are booleans, other YAML 1.1 forms (`yes`, `on`, dates, octal numbers...) stay text.
		 */
		fun readYaml(reader: Reader): ConfigurationTree{
			val options = LoaderOptions().apply {
				maxAliasesForCollections = 1000
			}
			val document = Yaml(SafeConstructor(options)).compose(reader) ?: return ConfigurationTree(MissingNode.getInstance())
			val converter = YamlConverter()
			return ConfigurationTree(converter.convert(document), converter.locations)
		}

		private class YamlConverter{
			val locations = IdentityHashMap<JsonNode, JsonLocation>()
			private val nodes = JsonNodeFactory.instance
			private val visiting = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())

			fun convert(node: Node): JsonNode{
				if(!visiting.add(node)){
					throw InvalidConfigurationException("Recursive alias at ${position(node.startMark)}")
				}
				try{
					val result = when(node){
						is ScalarNode -> scalar(node)
						is SequenceNode -> nodes.arrayNode().addAll(node.value.map(::convert))
						is MappingNode -> mapping(node)
						else -> throw InvalidConfigurationException("Unsupported YAML node at ${position(node.startMark)}")
					}
					locations[result] = node.startMark.let { JsonLocation(ContentReference.unknown(), -1L, -1L, it.line + 1, it.column + 1) }
					return result
				}
				finally{
					visiting.remove(node)
				}
			}

			private fun scalar(node: ScalarNode): JsonNode{
				val value = node.value
				if(!node.isPlain){
					return TextNode(value)
				}
				return when{
					node.tag == Tag.NULL -> nodes.nullNode()
					node.tag == Tag.BOOL && BOOLEAN.matches(value) -> nodes.booleanNode(value.equals("true", ignoreCase = true))
					node.tag == Tag.INT && INTEGER.matches(value) -> value.toLongOrNull()?.let(::LongNode) ?: nodes.numberNode(BigInteger(value))
					node.tag == Tag.FLOAT && DECIMAL.matches(value) -> DoubleNode(value.toDouble())
					else -> TextNode(value)
				}
			}

			private fun mapping(node: MappingNode): ObjectNode{
				val result = nodes.objectNode()
				val merged = mutableListOf<Node>()
				for(tuple in node.value){
					val key = tuple.keyNode
					if(key.tag == Tag.MERGE){
						merged += tuple.valueNode
						continue
					}
					if(key !is ScalarNode){
						throw InvalidConfigurationException("Keys must be plain values, at ${position(key.startMark)}")
					}
					if(result.has(key.value)){
						throw InvalidConfigurationException("Duplicate key '${key.value}' at ${position(key.startMark)}")
					}
					result.set<JsonNode>(key.value, convert(tuple.valueNode))
				}
				merged.flatMap { if(it is SequenceNode) it.value else listOf(it) }.forEach { source ->
					val values = convert(source) as? ObjectNode ?: throw InvalidConfigurationException("'<<' must be given mappings, at ${position(source.startMark)}")
					values.properties().filter { !result.has(it.key) }.forEach { result.set<JsonNode>(it.key, it.value) }
				}
				return result
			}

			private fun position(mark: Mark): String = "line ${mark.line + 1}, column ${mark.column + 1}"
		}
	}
}
