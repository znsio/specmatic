package io.specmatic.core.config.v3

import com.fasterxml.jackson.annotation.JsonIgnore
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
        @get:JsonIgnore open val value: String get() = transformUrl(defaultBaseUrl).toString()

        abstract fun transformUrl(defaultBaseUrl: URI): URI

        internal fun URI.withComponents(host: String? = null, port: Int? = null, path: String? = null): URI {
            return URI(
                this.scheme,
                null,
                host ?: this.host,
                port ?: this.port,
                path,
                this.query,
                this.fragment
            )
        }

        data class BaseUrl(val baseUrl: String, override val specs: List<String>) : ObjectValue() {
            override fun transformUrl(defaultBaseUrl: URI) = URI(baseUrl)
        }

        data class Host(val host: String, override val specs: List<String>) : ObjectValue() {
            override fun transformUrl(defaultBaseUrl: URI) = defaultBaseUrl.withComponents(host = host)
        }

        data class Port(val port: Int, override val specs: List<String>) : ObjectValue() {
            override fun transformUrl(defaultBaseUrl: URI) = defaultBaseUrl.withComponents(port = port)
        }

        data class BasePath(val basePath: String, override val specs: List<String>) : ObjectValue() {
            override fun transformUrl(defaultBaseUrl: URI) = defaultBaseUrl.withComponents(path = basePath)
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
        val specs = get("specs")?.takeIf(JsonNode::isArray)?.map(JsonNode::asText)?.takeIf(List<String>::isNotEmpty)
            ?: throw JsonMappingException(p, "Missing `specs` array or `specs` is empty")

        return when {
            has("baseUrl") -> Consumes.ObjectValue.BaseUrl(get("baseUrl").asText(), specs)
            has("host") -> Consumes.ObjectValue.Host(get("host").asText(), specs)
            has("port") -> Consumes.ObjectValue.Port(get("port").asInt(), specs)
            has("basePath") -> Consumes.ObjectValue.BasePath(get("basePath").asText(), specs)
            else -> throw JsonMappingException(p, "Object value must contain one of: baseUrl, host, port, or basePath")
        }
    }
}
