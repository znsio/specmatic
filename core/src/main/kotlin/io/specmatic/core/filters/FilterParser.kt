package io.specmatic.core.filters

import java.util.regex.Pattern

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