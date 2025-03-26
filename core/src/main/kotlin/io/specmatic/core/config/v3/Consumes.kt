package io.specmatic.core.config.v3

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode

sealed class Consumes {
    data class StringValue(@get:JsonValue val value: String) : Consumes()
    data class ObjectValue(val specs: List<String>, val baseUrl: String) : Consumes()
}

class ConsumesDeserializer : JsonDeserializer<List<Consumes>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<Consumes> {
        val node = p.codec.readTree<JsonNode>(p)
        return when {
            node.isArray -> {
                node.map { element ->
                    when {
                        element.isTextual -> Consumes.StringValue(element.asText())
                        element.isObject -> {
                            val specsNode = element["specs"] ?: throw JsonMappingException(p, "Missing 'specs' key")
                            if(specsNode.isArray.not()) throw JsonMappingException(p, "'specs' should be a list of string")

                            val specs = specsNode.map { it.asText() }
                            val baseUrl = element["baseUrl"]?.asText() ?: throw JsonMappingException(p, "Missing 'baseUrl' key")
                            Consumes.ObjectValue(specs, baseUrl)
                        }
                        else -> throw JsonMappingException(p, "Invalid type for consumes entry")
                    }
                }
            }
            else -> throw JsonMappingException(p, "Consumes should be an array")
        }
    }
}
