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
import io.specmatic.core.config.v3.Consumes

@JsonSerialize(using = ContractConfigSerializer::class)
@JsonDeserialize(using = ContractConfigDeserializer::class)
data class ContractConfig(
    val contractSource: ContractSource? = null,
    val provides: List<String>? = null,
    val consumes: List<String>? = null
) {
    constructor(source: Source) : this(
        contractSource = GitContractSource(source).takeIf { source.provider == SourceProvider.git }
            ?: FileSystemContractSource(source),
        provides = source.test,
        consumes = source.specsUsedAsStub()
    )

    fun transform(): Source {
        return this.contractSource?.transform(provides, consumes) ?: Source()
    }

    interface ContractSource {
        fun write(gen: JsonGenerator)
        fun transform(provides: List<String>?, consumes: List<String>?): Source
    }

    data class GitContractSource(
        val url: String? = null,
        val branch: String? = null
    ) : ContractSource {
        constructor(source: Source) : this(source.repository, source.branch)

        override fun write(gen: JsonGenerator) {
            gen.writeObjectFieldStart("git")
            gen.writeStringField("url", this.url)
            gen.writeStringField("branch", this.branch)
            gen.writeEndObject()
        }

        override fun transform(provides: List<String>?, consumes: List<String>?): Source {
            return Source(
                provider = SourceProvider.git,
                repository = this.url,
                branch = this.branch,
                test = provides,
                stub = consumes.orEmpty().map { Consumes.StringValue(it) }
            )
        }
    }

    data class FileSystemContractSource(
        val directory: String = "."
    ) : ContractSource {
        constructor(source: Source) : this(source.directory ?: ".")

        override fun write(gen: JsonGenerator) {
            gen.writeObjectFieldStart("filesystem")
            gen.writeStringField("directory", this.directory)
            gen.writeEndObject()
        }

        override fun transform(provides: List<String>?, consumes: List<String>?): Source {
            return Source(
                provider = SourceProvider.filesystem,
                directory = this.directory,
                test = provides,
                stub = consumes.orEmpty().map { Consumes.StringValue(it) }
            )
        }
    }
}

class ContractConfigSerializer : StdSerializer<ContractConfig>(ContractConfig::class.java) {
    override fun serialize(contract: ContractConfig, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        contract.contractSource?.write(gen)
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
                val gitNode = node["git"]
                val url = gitNode["url"]
                    .takeIf { it != null && it.asText().isNotBlank() } ?: throw JsonMappingException.from(
                    parser,
                    "Git contract source must have 'url' field"
                )
                val branch = gitNode["branch"]
                ContractConfig.GitContractSource(
                    url = url.asText(),
                    branch = branch?.asText()
                )
            }

            node.has("filesystem") -> {
                val filesystemNode = node["filesystem"]
                val directory = filesystemNode["directory"]
                    .takeIf { it != null && it.asText().isNotBlank() } ?: throw JsonMappingException.from(
                    parser,
                    "Filesystem contract source must have 'directory' field"
                )
                ContractConfig.FileSystemContractSource(
                    directory = directory.asText()
                )
            }

            else -> throw JsonMappingException.from(
                parser,
                "Contracts field must have either 'git' or 'filesystem' field"
            )
        }

        val provides = node["provides"]?.map { it.asText() }
        val consumes = node["consumes"]?.map { it.asText() }

        return ContractConfig(contractSource, provides, consumes)
    }
}