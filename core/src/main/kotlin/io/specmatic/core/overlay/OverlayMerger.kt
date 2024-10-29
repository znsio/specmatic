package io.specmatic.core.overlay

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option

class OverlayMerger {

    fun merge(
        baseContent: String,
        overlay: Overlay
    ): String {
        try {
            val yamlMapper = ObjectMapper(
                YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            )
            val rootNode = yamlMapper.readTree(baseContent) as ObjectNode
            val originalDocumentContext = JsonPath.using(
                Configuration.builder().options(Option.ALWAYS_RETURN_LIST).build()
            ).parse(
                ObjectMapper().writeValueAsString(rootNode)
            )

            val documentContext = JsonPath.using(
                Configuration.builder().options(Option.ALWAYS_RETURN_LIST).build()
            ).parse(
                ObjectMapper().writeValueAsString(rootNode)
            )


            overlay.updateMap.forEach { (jsonPath, content) ->
                val targetNodes = originalDocumentContext.read<List<Any>>(jsonPath)
                val existingNode = if (targetNodes.isNotEmpty()) targetNodes[0] else null

                when (existingNode) {
                    is Map<*, *> -> {
                        val mergedContent = (existingNode as Map<String, Any?>).toMutableMap()
                        (content as Map<String, Any?>).forEach { (key, value) ->
                            mergedContent[key] = value
                        }
                        documentContext.set(jsonPath, mergedContent)
                    }

                    is List<*> -> documentContext.add(jsonPath, content)

                    else -> documentContext.set(jsonPath, content)
                }
            }

            overlay.removalMap.filter { it.value }.forEach { (jsonPath, _) ->
                documentContext.delete(jsonPath)
            }

            return yamlMapper.writeValueAsString(
                ObjectMapper().readTree(documentContext.jsonString()) as ObjectNode
            ) ?: baseContent
        } catch(e: Exception) {
            println("Failed while applying overlay over the specification content with error: ${e.message}")
            return baseContent
        }
    }
}
