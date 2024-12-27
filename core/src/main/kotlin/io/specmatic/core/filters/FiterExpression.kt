package io.specmatic.core.filters

import java.util.regex.Pattern

sealed class FilterExpression {
    abstract fun matches(metadata: ScenarioMetadata): Boolean

    data class Equals(val key: String, val filterVal: String) : FilterExpression() {
        override fun matches(metadata: ScenarioMetadata): Boolean {
            val scenarioValue = getValue(metadata, key) ?: return false

            return filterVal.split(",")
                .asSequence()
                .mapNotNull { it.trim().takeIf { it.isNotBlank() } }
                .any { filterItem ->
                    scenarioValue.any { scenario ->
                        filterItem == scenario
                    }
                }
        }
    }

    data class NotEquals(val key: String, val filterVal: String) : FilterExpression() {
        override fun matches(metadata: ScenarioMetadata): Boolean {
            val scenarioVal = getValue(metadata, key) ?: return false

            return filterVal.split(",")
                .asSequence()
                .mapNotNull { it.trim().takeIf { it.isNotBlank() } }
                .all { filterItem ->
                    scenarioVal.none { scenario ->
                        filterItem.uppercase() == scenario.uppercase()
                    }
                }
        }
    }

    data class Regex(val key: String, val pattern: Pattern) : FilterExpression() {
        override fun matches(metadata: ScenarioMetadata): Boolean {
            val value = getValue(metadata, key) ?: return false

            return value.any { item -> pattern.matcher(item).matches() }
        }
    }

    data class NotRegex(val key: String, val pattern: Pattern) : FilterExpression() {
        override fun matches(metadata: ScenarioMetadata): Boolean {
            val value = getValue(metadata, key) ?: return false

            return value
                .asSequence()
                .none { item ->
                    pattern.matcher(item).matches()
                }
        }
    }

    data class Range(val key: String, val start: Int, val end: Int) : FilterExpression() {
        override fun matches(metadata: ScenarioMetadata): Boolean {
            val values = getValue(metadata, key) ?: return false
            val parsedValues = values.mapNotNull { it.toIntOrNull() }
            if (parsedValues.isEmpty()) return false

            return parsedValues
                .asSequence()
                .any { value ->
                    value in start..end
                }
        }
    }

    data class NotRange(val key: String, val start: Int, val end: Int) : FilterExpression() {
        override fun matches(metadata: ScenarioMetadata): Boolean {
            val values = getValue(metadata, key) ?: return false
            val parsedValues = values.mapNotNull { it.toIntOrNull() }
            if (parsedValues.isEmpty()) return false

            return parsedValues
                .asSequence()
                .none { value ->
                    value in start..end
                }
        }
    }

    companion object {
        private fun getValue(metadata: ScenarioMetadata, key: String): List<String>? {
            return when (ScenarioFilterTags.from(key)) {
                ScenarioFilterTags.METHOD -> listOf( metadata.method )
                ScenarioFilterTags.PATH -> listOf( metadata.path )
                ScenarioFilterTags.STATUS_CODE -> listOf( metadata.statusCode.toString() )
                ScenarioFilterTags.HEADER -> metadata.header.toList()
                ScenarioFilterTags.QUERY -> metadata.query.toList()
                ScenarioFilterTags.EXAMPLE_NAME  -> listOf( metadata.exampleName )
                else -> null
            }
        }
    }
}