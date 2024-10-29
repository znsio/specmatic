package io.specmatic.core.overlay

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.specmatic.core.log.logger

class OverlayParser {
    companion object {
        fun parseAndReturnUpdateMap(yamlContent: String): Map<String, Any> {
            try {
                val rootNode = yamlContent.rootNode()

                val actions = rootNode["actions"] as? List<Map<String, Any>>? ?: emptyList()
                val targetMap = mutableMapOf<String, Any>()

                actions.forEach { action ->
                    val target = action["target"] as? String
                    val update = action["update"]

                    if (target != null && update != null) targetMap[target] = update
                }

                return targetMap
            } catch(e: Exception) {
                logger.log("Skipped overlay based transformation for the specification because of an error occurred while parsing the update map: ${e.message}")
                return mutableMapOf()
            }
        }

        fun parseAndReturnRemovalMap(yamlContent: String): Map<String, Boolean> {
            try {
                val rootNode = yamlContent.rootNode()

                val actions = rootNode["actions"] as? List<Map<String, Any>>? ?: emptyList()
                val targetMap = mutableMapOf<String, Boolean>()

                actions.forEach { action ->
                    val target = action["target"] as? String
                    val remove = action["remove"] as? Boolean ?: false

                    if (target != null) targetMap[target] = remove
                }

                return targetMap
            } catch(e: Exception) {
                logger.log("Skipped overlay based transformation for the specification because of an error occurred while parsing the removal map: ${e.message}")
                return mutableMapOf()
            }
        }

        private fun String.rootNode(): Map<String, Any> {
            return ObjectMapper(YAMLFactory())
                .readValue(this, Map::class.java) as Map<String, Any>
        }
    }
}