package io.specmatic.core.config.v3

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import io.specmatic.core.Configuration.Companion.DEFAULT_BASE_URL
import io.specmatic.core.utilities.Flags
import java.net.URI

sealed class Consumes {
    data class StringValue(@get:JsonValue val value: String) : Consumes()
    sealed class ObjectValue : Consumes() {
        abstract val specs: List<String>
        private val defaultBaseUrl: URI get() = URI(Flags.getStringValue(Flags.SPECMATIC_BASE_URL) ?: DEFAULT_BASE_URL)

        fun toBaseUrl(defaultBaseUrl: String? = null): String {
            val baseUrl = defaultBaseUrl?.let(::URI) ?: this.defaultBaseUrl
            return toUrl(baseUrl).toString()
        }

        abstract fun toUrl(default: URI): URI

        data class FullUrl(val baseUrl: String, override val specs: List<String>) : ObjectValue() {
            override fun toUrl(default: URI) = URI(baseUrl)
        }

        data class PartialUrl(val host: String? = null, val port: Int? = null, val basePath: String? = null, override val specs: List<String>) : ObjectValue() {
            override fun toUrl(default: URI): URI {
                return URI(
                    default.scheme,
                    default.userInfo,
                    host ?: default.host,
                    port ?: default.port,
                    basePath ?: default.path,
                    default.query,
                    default.fragment
                )
            }
        }
    }
}

class ConsumesDeserializer : JsonDeserializer<List<Consumes>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<Consumes> {
        return p.codec.readTree<JsonNode>(p).takeIf(JsonNode::isArray)?.map { element ->
            when {
                element.isTextual -> Consumes.StringValue(element.asText())
                element.isObject -> element.parseObjectValue(p)
                else -> throw JsonMappingException(p, "Consumes entry must be string or object")
            }
        } ?: throw JsonMappingException(p, "Consumes should be an array")
    }

    private fun JsonNode.parseObjectValue(p: JsonParser): Consumes.ObjectValue {
        val validatedJsonNode = this.getValidatedJsonNode(p)
        val specs = validatedJsonNode.get("specs").map(JsonNode::asText)

        return when {
            has("baseUrl") -> Consumes.ObjectValue.FullUrl(get("baseUrl").asText(), specs)
            else -> Consumes.ObjectValue.PartialUrl(
                host = get("host")?.asText(),
                port = get("port")?.asInt(),
                basePath = get("basePath")?.asText(),
                specs = specs
            )
        }
    }

    private fun JsonNode.getValidatedJsonNode(p: JsonParser): JsonNode {
        val allowedFields = setOf("baseUrl", "host", "port", "basePath", "specs")
        val unknownFields = fieldNames().asSequence().filterNot(allowedFields::contains).toSet()
        if (unknownFields.isNotEmpty()) {
            throw JsonMappingException(p,
                "Unknown fields: ${unknownFields.joinToString(", ")}\nAllowed fields: ${allowedFields.joinToString(", ")}"
            )
        }

        val specsField = get("specs")
        when {
            specsField == null -> throw JsonMappingException(p, "Missing required field 'specs'")
            !specsField.isArray -> throw JsonMappingException(p, "'specs' must be an array")
            specsField.isEmpty -> throw JsonMappingException(p, "'specs' array cannot be empty")
            specsField.any { !it.isTextual } -> throw JsonMappingException(p, "'specs' must contain only strings")
        }

        val partialFields = listOf("host", "port", "basePath").filter(::has)
        val hasBaseUrl = has("baseUrl")
        val hasPartialFields = partialFields.isNotEmpty()
        when {
            hasBaseUrl && hasPartialFields -> throw JsonMappingException(p, "Cannot combine baseUrl with ${partialFields.joinToString(", ")}")
            !hasBaseUrl && !hasPartialFields -> throw JsonMappingException(p, "Must provide baseUrl or one or combination of host, port, and basePath")
        }

        return this
    }
}
