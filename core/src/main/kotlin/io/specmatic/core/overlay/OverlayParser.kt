package io.specmatic.core.overlay

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.specmatic.core.log.logger

class OverlayParser {
    companion object {

        fun parse(yamlContent: String): Overlay {
            return Overlay(
                updateMap = parseAndReturnUpdateMap(yamlContent),
                removalMap = parseAndReturnRemovalMap(yamlContent)
            )
        }

        private fun parseAndReturnUpdateMap(content: String): Map<String, Any?> {
            try {
                val targetMap = mutableMapOf<String, Any?>()

                content.actions().forEach { action ->
                    val target = action["target"] as? String
                    val update = action["update"]

                    if (target != null && action.containsKey("update")) targetMap[target] = update
                }

                return targetMap
            } catch (e: Exception) {
                logger.log("Skipped overlay based transformation for the specification because of an error occurred while parsing the update map: ${e.message}")
                return mutableMapOf()
            }
        }

        private fun parseAndReturnRemovalMap(content: String): Map<String, Boolean> {
            try {
                val targetMap = mutableMapOf<String, Boolean>()

                content.actions().forEach { action ->
                    val target = action["target"] as? String
                    val remove = action["remove"] as? Boolean ?: false

                    if (target != null) targetMap[target] = remove
                }

                return targetMap
            } catch (e: Exception) {
                logger.log("Skipped overlay based transformation for the specification because of an error occurred while parsing the removal map: ${e.message}")
                return mutableMapOf()
            }
        }

        private fun String.actions(): List<Map<String, Any>> {
            val rootNode = ObjectMapper(YAMLFactory())
                .readValue(this, Map::class.java) as Map<String, Any>

            return rootNode["actions"] as? List<Map<String, Any>>? ?: emptyList()
        }
    }
}