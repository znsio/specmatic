package io.specmatic.core.filters

import java.util.regex.Pattern

object FilterParser {

    fun parse(filter: String): List<FilterGroup> {
        if (filter.isBlank()) return emptyList()

        val normalizedFilter = normalizeFilter(filter)

        if(!isValidFilter(normalizedFilter)) return emptyList()

        val tokens = tokenize(normalizedFilter)

        val filterGroups = parseTokens(tokens)

        return filterGroups;
    }

    private fun isValidFilter(filter: String): Boolean {
        val validKeys = setOf("METHOD", "PATH", "STATUS", "EXAMPLE-NAME", "HEADERS", "QUERY-PARAMS")
        val regex = Regex("\\s*(\\w+)\\s*(=|!=)\\s*([\\w/*{}, ]+)")
        val logicalOperators = setOf("&&", "||", "!")

        var balance = 0

        filter.split(" ").forEach { token ->
            when {
                token == "(" -> balance++
                token == ")" -> {
                    balance--
                    if (balance < 0) return false // More closing brackets than opening
                }
                logicalOperators.contains(token) -> Unit
                regex.matches(token) -> {
                    val key = regex.find(token)?.groupValues?.get(1) ?: return false
                    if (key !in validKeys) return false
                }
            }
        }

        return balance == 0 // Ensure no unmatched parentheses
    }

    /**
     * Support AND, &&; OR, ||; NOT, ! for conditions
     * And =, == for comparisons
     */
    private fun normalizeFilter(filter: String): String {
        return filter.replace(Regex("\\bAND\\b|\\bOR\\b|\\bNOT\\b|=="), { matchResult ->
            when (matchResult.value) {
                "AND" -> "&&"
                "OR" -> "||"
                "NOT" -> "!"
                "==" -> "="
                else -> matchResult.value
            }
        })
    }

    private fun tokenize(filter: String): List<String> {
        val regex = Regex(
            """(?:[A-Za-z]+!=[^\s()!&|]+)|(?:[A-Za-z]+=[^\s()!&|]+)|\(|\)|!|&&|\|\||[^\s()!&|]+"""
        )
        return regex.findAll(filter.trim())
            .map { it.value }
            .toList()
    }


    private fun parseTokens(tokens: List<String>): List<FilterGroup> {
        val stack = mutableListOf<Any>()
        val result = mutableListOf<FilterGroup>()
        var currentGroup = mutableListOf<Any>()

        tokens.forEach { token ->
            when (token) {
                "(" -> {

                    if(currentGroup.any{it is FilterExpression}) {
                        result.add(buildFilterGroup(currentGroup))
                        currentGroup = mutableListOf<Any>()
                    }

                    stack.add(token)
                }
                ")" -> {
                    if (stack.isNotEmpty() && stack.last() == "(") {
                        stack.removeAt(stack.lastIndex)
                    }

                    val filterGroup = buildFilterGroup(currentGroup)

                    val isNegated = if (stack.isNotEmpty() && stack.last() == "!") {
                        stack.removeAt(stack.size - 1)
                        true
                    } else {
                        false
                    }
                    filterGroup.isNegated = isNegated
                    result.add(filterGroup)
                    currentGroup = mutableListOf<Any>()
                }
                "&&"-> currentGroup.add(token)
                "||"-> {
                    if(currentGroup.isNotEmpty()) {

                        val filterGroup = buildFilterGroup(currentGroup)

                        result.add(filterGroup)
                        currentGroup = mutableListOf<Any>()
                    }
                }
                "!" -> stack.add(token) // Add negation operator
                else -> currentGroup.add(parseCondition(token))
            }
        }

        // Process any remaining tokens in `currentGroup`
        if (currentGroup.isNotEmpty()) {
            result.add(buildFilterGroup(currentGroup))
        }

        return result
    }

    private fun buildFilterGroup(tokens: List<Any>): FilterGroup {
        val filters = mutableListOf<FilterExpression>()
        val subGroups = mutableListOf<FilterGroup>()
        var isAndOperation = tokens.first() == "&&"

        tokens.forEach { token ->
            when (token) {
                is FilterExpression -> filters.add(token)
                is FilterGroup -> subGroups.add(token)
            }
        }

        return FilterGroup(filters, subGroups, isAndOperation)
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