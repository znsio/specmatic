package io.specmatic.core.config.v2

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import io.specmatic.core.Source
import io.specmatic.core.SourceProvider
import io.specmatic.core.pattern.ContractException

@JsonSerialize(using = ContractConfigSerializer::class)
@JsonDeserialize(using = ContractConfigDeserializer::class)
data class ContractConfig(
    val contractSource: ContractSource? = null,
    val provides: List<String>? = null,
    val consumes: List<String>? = null
) {
    constructor(source: Source) : this(
        contractSource = ContractSource.Git(source).takeIf { source.provider == SourceProvider.git }
            ?: ContractSource.FileSystem(source),
        provides = source.test,
        consumes = source.stub
    )

    fun transform(): Source {
        return when (val contractSource = this.contractSource) {
            is ContractSource.Git -> Source(
                provider = SourceProvider.git,
                repository = contractSource.url,
                branch = contractSource.branch,
                test = provides,
                stub = consumes
            )

            is ContractSource.FileSystem -> Source(
                provider = SourceProvider.filesystem,
                directory = contractSource.directory,
                test = provides,
                stub = consumes,
            )

            else -> Source()
        }
    }
}

sealed class ContractSource {
    data class Git(
        val url: String? = null,
        val branch: String? = null
    ) : ContractSource() {
        constructor(source: Source) : this(source.repository, source.branch)
    }

    data class FileSystem(
        val directory: String? = null
    ) : ContractSource() {
        constructor(source: Source) : this(source.directory)
    }
}


class ContractConfigSerializer : StdSerializer<ContractConfig>(ContractConfig::class.java) {
    override fun serialize(contract: ContractConfig, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        when (val contractSource = contract.contractSource) {
            is ContractSource.Git -> {
                gen.writeObjectFieldStart("git")
                gen.writeStringField("url", contractSource.url)
                gen.writeStringField("branch", contractSource.branch)
                gen.writeEndObject()
            }

            is ContractSource.FileSystem -> {
                gen.writeObjectFieldStart("filesystem")
                gen.writeStringField("directory", contractSource.directory)
                gen.writeEndObject()
            }

            else -> throw ContractException("Unsupported Contract Source type.")
        }
        gen.writeObjectField("provides", contract.provides)
        gen.writeObjectField("consumes", contract.consumes)
        gen.writeEndObject()
    }
}

class ContractConfigDeserializer : StdDeserializer<ContractConfig>(ContractConfig::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): ContractConfig {
        val node: JsonNode = parser.codec.readTree(parser)

        val contractSource = when {
            node.has("git") -> {
                val gitNode = node.get("git")
                ContractSource.Git(
                    url = gitNode.get("url").asText(),
                    branch = gitNode.get("branch").asText()
                )
            }

            node.has("filesystem") -> {
                val filesystemNode = node.get("filesystem")
                ContractSource.FileSystem(
                    directory = filesystemNode.get("directory").asText()
                )
            }

            else -> throw JsonMappingException.from(
                parser,
                "Contracts field must have either 'git' or 'filesystem' field"
            )
        }

        val provides = node.get("provides").map { it.asText() }
        val consumes = node.get("consumes").map { it.asText() }

        return ContractConfig(contractSource, provides, consumes)
    }
}