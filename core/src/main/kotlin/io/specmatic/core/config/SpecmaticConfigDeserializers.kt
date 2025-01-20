package io.specmatic.core.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import io.specmatic.core.config.v2.ContractConfig

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