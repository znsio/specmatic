package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import io.specmatic.core.filters.FilterSymbols.ComparisonOperator
import io.specmatic.core.filters.FilterSymbols.LogicalOperator
import io.specmatic.core.filters.FilterSymbols.Parenthesis
import io.specmatic.core.filters.FilterSymbols.SpecialSymbol
import org.jetbrains.annotations.VisibleForTesting
import java.util.regex.Pattern

data class FilterSyntax(val filter: String) {

    fun parse(): List<FilterGroup> {
        validateFilter()
        evaluateFilter()
        return emptyList()
//        val tokens = tokenize()
//        val filterGroups = parseTokens(tokens)
//        return filterGroups
    }

    @VisibleForTesting
    internal fun validateFilter() {
        try {
            val expression = Expression(filter)
            expression.validate()
        } catch (e: Exception) {
            throw IllegalArgumentException("Expression is incorrect")
        }
    }

        @VisibleForTesting
        fun evaluateFilter() {
            try {
//                val expression = CustomExpression(filter)
                val expression = Expression(filter)
                expression.with("METHOD","POST").with("STATUS",200).evaluate()
            }
            catch (e : Exception)
            {
                throw IllegalArgumentException("Expression is incorrect")
            }


//        filter.takeIf { it.isBlank() }?.let { return@let false }
//
//        val validKeys = ScenarioFilterTags.entries.map{it.key}.toSet()
//        val regex = Regex("\\s*(\\w+)\\s*(=|!=)\\s*([\\w/*{}, ]+)")
//
//        var balance = 0
//
//        filter.split(" ").forEach { token ->
//            when {
//                token == Parenthesis.OPEN.symbol -> balance++
//                token == Parenthesis.CLOSE.symbol -> {
//                    balance--
//                    if (balance < 0) return false
//                }
//                LogicalOperator.contains(token) -> Unit
//                regex.matches(token) -> {
//                    val key = regex.find(token)?.groupValues?.get(1) ?: return false
//                    if (key !in validKeys) return false
//                }
//            }
//        }
//
//        return balance == 0
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
        var groupIndex = 0
        var presentGroup = FilterGroup()
        tokens.forEach { token ->
            when (token) {
                Parenthesis.OPEN.symbol -> {
                    if(currentGroup.any{it is FilterExpression}) {
                        presentGroup = buildFilterGroup(currentGroup)
                        currentGroup = mutableListOf()
                    }
                    if(stack.contains(Parenthesis.OPEN.symbol))
                    {
                        groupIndex ++
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

                    if(groupIndex > 0) {
                        val previousGroup = currentGroup
                        currentGroup  = mutableListOf()
                        presentGroup.subGroups.add(buildFilterGroup(previousGroup))
                        groupIndex --
                    }
                    if(currentGroup.isNotEmpty()) {
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
                }
                LogicalOperator.AND.symbol-> {
                    stack.add(token)
                }
                LogicalOperator.OR.symbol-> {
                    if(currentGroup.isNotEmpty()) {
                        val filterGroup = buildFilterGroup(currentGroup)
                        presentGroup.subGroups.add(filterGroup)
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
        result.add(presentGroup)
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
                    key == ScenarioFilterTags.STATUS_CODE.key && value.endsWith(SpecialSymbol.DOUBLE_DIGIT_WILDCARD.symbol) -> {
                        val rangeStart = value[0].digitToInt() * 100
                        val rangeEnd = rangeStart + 99
                        if (operator == ComparisonOperator.EQUAL.symbol)
                            FilterExpression.Range(key, rangeStart, rangeEnd)
                        else
                            FilterExpression.NotRange(key, rangeStart, rangeEnd)
                    }
                    key == ScenarioFilterTags.STATUS_CODE.key && value.endsWith(SpecialSymbol.SINGLE_DIGIT_WILDCARD.symbol) -> {
                        val rangeStart = value.substring(0, 2).toInt() * 10
                        val rangeEnd = rangeStart + 9
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
    var subGroups: MutableList<FilterGroup> = mutableListOf(),
    val isAndOperation: Boolean = false,
    var isNegated: Boolean = false
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        var filterMatches : List<Boolean> = emptyList()
        if(filters.isNotEmpty()) {
             filterMatches = filters.map { it.matches(metadata) }
        }

        val subGroupResults = mutableListOf<Boolean>()
        var tempAndResult: Boolean? = null
        if(subGroups.isNotEmpty()) {
            for ((index, group) in subGroups.withIndex()) {
                val currentResult = group.filters.map { it.matches(metadata) }
                val res = currentResult.all { it }
                if (index == 0) {
                    tempAndResult = res
                } else if (group.isAndOperation) {
                    tempAndResult = res.and(res)
                } else {
                    if (tempAndResult != null) {
                        subGroupResults.add(tempAndResult)
                        tempAndResult = null
                    }

                    subGroupResults.add(res)
                }
            }

            if (tempAndResult != null) {
                subGroupResults.add(tempAndResult)
            }
        }
        val allMatches = filterMatches + subGroupResults.any { it }
        val groupResult = allMatches.all { it }

        return if (isNegated) !groupResult else groupResult

        //subgroups =>
        // subgroup1 - filters:{METHOD=GET}
        // subgroup2 - filters:{METHOD=POST}, {STATUS=200}
    }
}