package io.specmatic.core.filters

import java.util.regex.Pattern
import io.specmatic.core.filters.FilterSymbols.LogicalOperator
import io.specmatic.core.filters.FilterSymbols.Parenthesis
import io.specmatic.core.filters.FilterSymbols.ComparisonOperator
import io.specmatic.core.filters.FilterSymbols.SpecialSymbol
import org.jetbrains.annotations.VisibleForTesting

data class FilterSyntax(val filter: String) {

    fun parse(): List<FilterGroup> {
        if (!isValidFilter()) return emptyList()

        val tokens = tokenize()

        val filterGroups = parseTokens(tokens)

        return filterGroups
    }

    @VisibleForTesting
    internal fun isValidFilter(): Boolean {

        filter.takeIf { it.isBlank() }?.let { return@let false }

        val validKeys = ScenarioFilterTags.entries.map{it.key}.toSet()
        val regex = Regex("\\s*(\\w+)\\s*(=|!=)\\s*([\\w/*{}, ]+)")

        var balance = 0

        filter.split(" ").forEach { token ->
            when {
                token == Parenthesis.OPEN.symbol -> balance++
                token == Parenthesis.CLOSE.symbol -> {
                    balance--
                    if (balance < 0) return false
                }
                LogicalOperator.contains(token) -> Unit
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
                Parenthesis.OPEN.symbol -> {
                    if(currentGroup.any{it is FilterExpression}) {
                        result.add(buildFilterGroup(currentGroup))
                        currentGroup = mutableListOf()
                    }

                    stack.add(token)
                }
                Parenthesis.CLOSE.symbol -> {
                    if (stack.isNotEmpty() && stack.last() == Parenthesis.OPEN.symbol) {
                        stack.removeAt(stack.lastIndex)
                    }

                    if(stack.isNotEmpty() && stack.first() == LogicalOperator.AND.symbol) {
                        currentGroup.add(0, stack.removeFirst())
                    }

                    val filterGroup = buildFilterGroup(currentGroup)

                    val isNegated = if (stack.isNotEmpty() && stack.last() == LogicalOperator.NOT.symbol) {
                        stack.removeAt(stack.size - 1)
                        true
                    } else {
                        false
                    }
                    filterGroup.isNegated = isNegated
                    result.add(filterGroup)
                    currentGroup = mutableListOf()
                }
                LogicalOperator.AND.symbol-> {
                    stack.add(token)
                }
                LogicalOperator.OR.symbol-> {
                    if(currentGroup.isNotEmpty()) {

                        val filterGroup = buildFilterGroup(currentGroup)

                        result.add(filterGroup)
                        currentGroup = mutableListOf()
                    }
                }
                LogicalOperator.NOT.symbol -> stack.add(token)
                else -> {
                    if(stack.isNotEmpty() && stack.last() == LogicalOperator.AND.symbol) {
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
        val isAndOperation = tokens.first() == LogicalOperator.AND.symbol

        tokens.forEach { token ->
            when (token) {
                is FilterExpression -> filters.add(token)
                is FilterGroup -> subGroups.add(token)
            }
        }

        return FilterGroup(filters, subGroups, isAndOperation)
    }

    private fun parseCondition(condition: String): FilterExpression {
        val operatorIndex = condition.indexOf(ComparisonOperator.NOT_EQUAL.symbol).takeIf { it != -1 }
            ?: condition.indexOf(ComparisonOperator.EQUAL.symbol).takeIf { it != -1 }
            ?: throw IllegalArgumentException("Invalid condition format: $condition. No valid operator found.")

        val operator = if (condition.substring(operatorIndex, operatorIndex + 2) == ComparisonOperator.NOT_EQUAL.symbol) ComparisonOperator.NOT_EQUAL.symbol else ComparisonOperator.EQUAL.symbol
        val key = condition.substring(0, operatorIndex).trim()
        val value = condition.substring(operatorIndex + operator.length).trim()

        return when (operator) {
            ComparisonOperator.EQUAL.symbol, ComparisonOperator.NOT_EQUAL.symbol -> {
                when {
                    value.contains(SpecialSymbol.WILDCARD.symbol)  -> {
                        val pattern = Pattern.compile(value.replace("*", ".*").replace("?", "."))
                        if (operator == ComparisonOperator.EQUAL.symbol)
                            FilterExpression.Regex(key, pattern)
                        else
                            FilterExpression.NotRegex(key, pattern)
                    }
                    key == ScenarioFilterTags.STATUS_CODE.key && value.contains(SpecialSymbol.RANGE.symbol) -> {
                        val rangeStart = value[0].digitToInt() * 100
                        val rangeEnd = rangeStart + 99
                        if (operator == ComparisonOperator.EQUAL.symbol)
                            FilterExpression.Range(key, rangeStart, rangeEnd)
                        else
                            FilterExpression.NotRange(key, rangeStart, rangeEnd)
                    }
                    operator == ComparisonOperator.EQUAL.symbol -> FilterExpression.Equals(key, value)
                    else -> FilterExpression.NotEquals(key, value)
                }
            }
            else -> throw IllegalArgumentException("Unsupported operator: $operator")
        }
    }
}


data class FilterGroup(
    val filters: List<FilterExpression> = emptyList(),
    val subGroups: List<FilterGroup> = emptyList(),
    val isAndOperation: Boolean = false,
    var isNegated: Boolean = false
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        val filterMatches = filters.map { it.matches(metadata) }

        val subGroupMatches = subGroups.map { it.isSatisfiedBy(metadata) }

        val allMatches = filterMatches + subGroupMatches
        val groupResult = allMatches.all { it }

        return if (isNegated) !groupResult else groupResult
    }
}