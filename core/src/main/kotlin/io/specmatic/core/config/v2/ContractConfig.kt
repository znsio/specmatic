package io.specmatic.core.config.v2

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.specmatic.core.Source
import io.specmatic.core.SourceProvider
import io.specmatic.core.config.ContractConfigSerializer

@JsonSerialize(using = ContractConfigSerializer::class)
@JsonDeserialize(using = ContractConfigDeserializer::class)
data class ContractConfig(
    val contractSource: ContractSource = FileSystemContractSource(),
    val provides: List<String>? = null,
    val consumes: List<String>? = null
) {
    constructor(source: Source) : this(
        contractSource = GitContractSource(source).takeIf { source.provider == SourceProvider.git }
            ?: FileSystemContractSource(source),
        provides = source.test,
        consumes = source.stub
    )

    fun transform(): Source {
        return this.contractSource.transform(provides, consumes)
    }

    interface ContractSource {
        fun write(gen: JsonGenerator)
        fun isEmpty(): Boolean
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
                stub = consumes
            )
        }

        override fun isEmpty(): Boolean {
            return false
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
                stub = consumes
            )
        }

        override fun isEmpty(): Boolean {
            return directory == "."
        }
    }
}

class ContractConfigDeserializer : JsonDeserializer<ContractConfig>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): ContractConfig {
        val node: JsonNode = parser.codec.readTree(parser)

        val contractSource = when {
            node.has("git") -> {
                val gitNode = node.get("git")
                ContractConfig.GitContractSource(
                    url = gitNode.get("url").asText(),
                    branch = gitNode.get("branch").asText()
                )
            }

            node.has("filesystem") -> {
                val filesystemNode = node.get("filesystem")
                ContractConfig.FileSystemContractSource(
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