package io.specmatic.core.config.v3

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

@JsonSerialize(using = ContractConfigSerializerV2::class)
@JsonDeserialize(using = ContractConfigDeserializerV2::class)
data class ContractConfigV2(
    val contractSource: ContractSourceV2? = null,
    val provides: List<String>? = null,
    val consumes: List<Consumes>? = null
) {
    constructor(source: Source) : this(
        contractSource = GitContractSourceV2(source).takeIf { source.provider == SourceProvider.git }
            ?: FileSystemContractSourceV2(source),
        provides = source.test,
        consumes = source.stub
    )

    fun transform(): Source {
        return this.contractSource?.transform(provides, consumes) ?: Source(test = provides, stub = consumes)
    }

    interface ContractSourceV2 {
        fun write(gen: JsonGenerator)
        fun transform(provides: List<String>?, consumes: List<Consumes>?): Source
    }

    data class GitContractSourceV2(
        val url: String? = null,
        val branch: String? = null
    ) : ContractSourceV2 {
        constructor(source: Source) : this(source.repository, source.branch)

        override fun write(gen: JsonGenerator) {
            gen.writeObjectFieldStart("git")
            gen.writeStringField("url", this.url)
            gen.writeStringField("branch", this.branch)
            gen.writeEndObject()
        }

        override fun transform(provides: List<String>?, consumes: List<Consumes>?): Source {
            return Source(
                provider = SourceProvider.git,
                repository = this.url,
                branch = this.branch,
                test = provides,
                stub = consumes.orEmpty()
            )
        }
    }

    data class FileSystemContractSourceV2(
        val directory: String = "."
    ) : ContractSourceV2 {
        constructor(source: Source) : this(source.directory ?: ".")

        override fun write(gen: JsonGenerator) {
            gen.writeObjectFieldStart("filesystem")
            gen.writeStringField("directory", this.directory)
            gen.writeEndObject()
        }

        override fun transform(provides: List<String>?, consumes: List<Consumes>?): Source {
            return Source(
                provider = SourceProvider.filesystem,
                directory = this.directory,
                test = provides,
                stub = consumes.orEmpty()
            )
        }
    }
}

class ContractConfigSerializerV2 : StdSerializer<ContractConfigV2>(ContractConfigV2::class.java) {
    override fun serialize(contract: ContractConfigV2, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        contract.contractSource?.write(gen)
        gen.writeObjectField("provides", contract.provides)
        gen.writeObjectField("consumes", contract.consumes)
        gen.writeEndObject()
    }
}

class ContractConfigDeserializerV2 : StdDeserializer<ContractConfigV2>(ContractConfigV2::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): ContractConfigV2 {
        val node: JsonNode = parser.codec.readTree(parser)

        val contractSource = when {
            node.has("git") -> {
                val gitNode = node.get("git")
                ContractConfigV2.GitContractSourceV2(
                    url = gitNode.get("url").asText(),
                    branch = gitNode.get("branch").asText()
                )
            }

            node.has("filesystem") -> {
                val filesystemNode = node.get("filesystem")
                ContractConfigV2.FileSystemContractSourceV2(
                    directory = filesystemNode.get("directory").asText()
                )
            }

            else -> throw JsonMappingException.from(
                parser,
                "Contracts field must have either 'git' or 'filesystem' field"
            )
        }

        val provides = if(node.has("provides"))
            node.get("provides").map { it.asText() }
        else emptyList()
        val consumes = if(node.has("consumes")) {
            node.get("consumes").map {
                if(it.isObject) {
                    Consumes.ObjectValue(
                        specs = it.get("specs").map { specPath -> specPath.asText() },
                        port = it.get("port").asInt()
                    )
                } else {
                    Consumes.StringValue(it.asText())
                }
            }
        } else emptyList()

        return ContractConfigV2(contractSource, provides, consumes)
    }
}