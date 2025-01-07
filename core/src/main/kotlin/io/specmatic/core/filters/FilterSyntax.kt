package io.specmatic.core.filters

import java.util.regex.Pattern

data class FilterSyntax(val filter: String) {

    fun parse(): List<FilterGroup> {
        if (!isValidFilter()) return emptyList()

        val tokens = tokenize()

        val filterGroups = parseTokens(tokens)

        return filterGroups;
    }

    private fun isValidFilter(): Boolean {
        filter.takeIf { it.isBlank() }?.let { return false }

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

        return balance == 0
    }

    private fun tokenize(): List<String> {
        val regex = Regex("""
        [A-Za-z]+(?:=|!=)[^()\s&|]+(?:\([^()]*\))?|  
        \(|
        \)|
        !(?=\()|
        &&|
        \|\|
        """.trimIndent().replace(Regex("#.*\\n"), "").replace("\\s+".toRegex(), ""))

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

                    if(stack.isNotEmpty() && stack.first() == "&&") {
                        currentGroup.add(0, stack.removeFirst())
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
                "&&"-> {
                    stack.add(token)
                }
                "||"-> {
                    if(currentGroup.isNotEmpty()) {

                        val filterGroup = buildFilterGroup(currentGroup)

                        result.add(filterGroup)
                        currentGroup = mutableListOf<Any>()
                    }
                }
                "!" -> stack.add(token)
                else -> {
                    if(stack.isNotEmpty() && stack.last() == "&&") {
                        currentGroup.add(stack.removeLast())
                    }
                    currentGroup.add(parseCondition(token))
                }
            }
        }

        if (currentGroup.isNotEmpty()) {
            result.add(buildFilterGroup(currentGroup))
        }

        return result
    }

    private fun buildFilterGroup(tokens: List<Any>): FilterGroup {
        val filters = mutableListOf<FilterExpression>()
        val subGroups = mutableListOf<FilterGroup>()
        val isAndOperation = tokens.first() == "&&"

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