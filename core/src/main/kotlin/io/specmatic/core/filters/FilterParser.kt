package io.specmatic.core.filters

import java.util.regex.Pattern

object FilterParser {

    fun parse(filter: String): List<FilterGroup> {
        if (filter.isBlank()) return emptyList()

        val normalizedFilter = normalizeFilter(filter)

        if(!isValidFilter(normalizedFilter)) return emptyList()

        val tokens = tokenize(normalizedFilter)

        val groups = parseTokens(tokens)

        return groups;
    }

    private fun isValidFilter(filter: String): Boolean {
        val validKeys = setOf("METHOD", "PATH", "STATUS", "EXAMPLE-NAME", "HEADERS", "QUERY-PARAMS")
        val regex = Regex("\\s*(\\w+)\\s*(=|!=)\\s*([\\w/\\*\\{\\}, ]+)")
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
        val regex = Regex("\\(|\\)|!|&&|\\|\\||[^\\s()!&|]+")
        return regex.findAll(filter).map { it.value }.toList()
    }

    private fun parseTokens(tokens: List<String>): List<FilterGroup> {
        val stack = mutableListOf<Any>() // Temporary stack for tokens and intermediate groups
        val result = mutableListOf<FilterGroup>() // Final list of FilterGroups
        var currentGroup = mutableListOf<Any>() // Tracks tokens for the current FilterGroup

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
//                    // Process tokens within the parentheses
//                    val groupTokens = mutableListOf<Any>()
//                    while (stack.isNotEmpty() && stack.last() != "(") {
//                        groupTokens.add(0, stack.removeAt(stack.size - 1))
//                    }
//                    if (stack.isEmpty() || stack.removeAt(stack.size - 1) != "(") {
//                       // shouldn't happen as we validated already, but just in case
//                        // return empty
//                    }

//                    if (groupTokens.isEmpty()) {
//                        throw IllegalArgumentException("Empty group inside parentheses is not allowed")
//                    }
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
//                    stack.add(subgroup)
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

        // Process any remaining subgroups or tokens in the stack
//        while (stack.isNotEmpty()) {
//            val item = stack.removeAt(stack.size - 1)
//            if (item is FilterGroup) {
//                result.add(item)
//            } else {
//                throw IllegalArgumentException("Invalid structure in tokens")
//            }
//        }

        return result
    }

    private fun buildFilterGroup(tokens: List<Any>): FilterGroup {
        val filters = mutableListOf<FilterExpression>()
        val subGroups = mutableListOf<FilterGroup>()
        var isAndOperation = tokens.first() == "&&"

        tokens.forEach { token ->
            when (token) {
//                "&&" -> isAndOperation = true
//                "||" -> {
//                    throw IllegalArgumentException("Unexpected OR in a single group")
//                }
                is FilterExpression -> filters.add(token)
                is FilterGroup -> subGroups.add(token)
//                else -> throw IllegalArgumentException("Invalid token in group: $token")
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