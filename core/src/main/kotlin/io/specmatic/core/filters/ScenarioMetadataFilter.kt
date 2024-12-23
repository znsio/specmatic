package io.specmatic.core.filters

import java.util.regex.Pattern

data class ScenarioMetadataFilter(
    // Groups are created for || based condition, as they have lower precedence than AND.
    val filterGroups: List<FilterGroup> = emptyList()
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        if (filterGroups.isEmpty()) return true
        return filterGroups.any { it.isSatisfiedBy(metadata) }
    }

    companion object {
        fun from(filter: String): ScenarioMetadataFilter {
            if (filter.isEmpty()) return ScenarioMetadataFilter()
            val parsedFilters = FilterParser.parse(filter)
            return ScenarioMetadataFilter(filterGroups = parsedFilters)
        }

        fun <T> filterUsing(
            items: Sequence<T>,
            scenarioMetadataFilter: ScenarioMetadataFilter,
            toScenarioMetadata: (T) -> ScenarioMetadata
        ): Sequence<T> {
            val returnItems = items.filter {
                scenarioMetadataFilter.isSatisfiedBy(toScenarioMetadata(it))
            }
            return returnItems
        }
    }
}

// FilterGroup: Represents a group of filters combined || i.e. OR.
data class FilterGroup(val filters: List<FilterExpression>, val isAndOperation: Boolean = true) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        val matches = filters.map { it.matches(metadata) }
        return if (isAndOperation) matches.all { it } else matches.any { it }
    }
}

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


object FilterParser {

    fun parse(filter: String): List<FilterGroup> {
        if (filter.isBlank()) return emptyList()

        // Normalize operators
        val normalizedFilter = normalizeFilter(filter)

        // Check if the filter contains "||". If not, treat the entire filter as a single && group.
        if (!normalizedFilter.contains("||")) {
            val andFilters = normalizedFilter.split("&&").mapNotNull { it.trim().takeIf { it.isNotBlank() } }
            val filterExpressions = andFilters.map { parseCondition(it) }
            return listOf(FilterGroup(filters = filterExpressions, isAndOperation = true))
        }

        // Handle filters with "||" groups
        return normalizedFilter.split("||")
            .mapNotNull { it.trim().takeIf { it.isNotBlank() } }
            .map { orGroup ->
                val andFilters = orGroup.split("&&").mapNotNull { it.trim().takeIf { it.isNotBlank() } }
                val filterExpressions = andFilters.map { parseCondition(it) }
                FilterGroup(filters = filterExpressions, isAndOperation = andFilters.size > 1)
            }
    }

    /**
     * Support AND, &&, OR, ||, for conditions
     * And =, == for comparisons
     */
    private fun normalizeFilter(filter: String): String {
        return filter.replace(Regex("\\bAND\\b|\\bOR\\b|=="), { matchResult ->
            when (matchResult.value) {
                "AND" -> "&&"
                "OR" -> "||"
                "==" -> "="
                else -> matchResult.value
            }
        })
    }

    private fun parseCondition(condition: String): FilterExpression {
        val operatorIndex = condition.indexOf("!=").takeIf { it != -1 }
            ?: condition.indexOf("=").takeIf { it != -1 }
            ?: throw IllegalArgumentException("Invalid condition format: $condition. No valid operator found.")

        val operator = if (condition.substring(operatorIndex, operatorIndex + 2) == "!=") "!=" else "="
        val key = condition.substring(0, operatorIndex).trim()
        val value = condition.substring(operatorIndex + operator.length).trim()

        return when (operator) {
            "=", "!=" -> {
                when {
                    value.contains("*") || value.contains("?") -> {
                        val pattern = Pattern.compile(value.replace("*", ".*").replace("?", "."))
                        if (operator == "=")
                            FilterExpression.Regex(key, pattern)
                        else
                            FilterExpression.NotRegex(key, pattern)
                    }
                    key == "STATUS" && value.contains("xx") -> {
                        val rangeStart = value[0].digitToInt() * 100
                        val rangeEnd = rangeStart + 99
                        if (operator == "=")
                            FilterExpression.Range(key, rangeStart, rangeEnd)
                        else
                            FilterExpression.NotRange(key, rangeStart, rangeEnd)
                    }
                    operator == "=" -> FilterExpression.Equals(key, value)
                    else -> FilterExpression.NotEquals(key, value)
                }
            }
            else -> throw IllegalArgumentException("Unsupported operator: $operator")
        }
    }
}