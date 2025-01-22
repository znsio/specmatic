package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import java.util.regex.Pattern

@FunctionParameter(name = "value")
class CSVFunction : AbstractFunction() {
    override fun evaluate(
        expression: Expression, functionToken: Token, vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val inputString = parameterValues[0].stringValue
        val (label, operator, values) = parseCondition(inputString)
        val scenarioValue = expression.dataAccessor.getData(label).value.toString()
        val result = evaluateCondition(label, operator, values, scenarioValue)

        return EvaluationValue.of(result, ExpressionConfiguration.defaultConfiguration())
    }

    private fun evaluateCondition(label: String, operator: String, values: List<String>, scenarioValue: String): Boolean {

        fun checkCondition(value: String, isMatch: Boolean): Boolean {
            val matches = when (label) {
                ScenarioFilterTags.STATUS_CODE.key -> value == scenarioValue || matchesStatusCode(value, scenarioValue)
                ScenarioFilterTags.PATH.key -> value == scenarioValue || matchesPath(value, scenarioValue)
                else -> value == scenarioValue
            }
            return if (isMatch) matches else !matches
        }

        return when (operator) {
            "=" -> values.any { checkCondition(it, isMatch = true) }
            "!=" -> values.all { checkCondition(it, isMatch = false) }
            else -> throw IllegalArgumentException("Unsupported operator: $operator")
        }
    }


    private fun matchesStatusCode(value: String, scenarioValue: String): Boolean {
        return isInRange(value, scenarioValue)
    }

    private fun matchesPath(value: String, scenarioValue: String): Boolean {
        return value.contains("*") && Pattern.compile(value.replace("*", ".*")).matcher(scenarioValue.toString()).matches()
    }


    private fun parseCondition(condition: String): Triple<String, String, List<String>> {
        val operator = if (condition.contains("!=")) "!=" else "="
        val parts = condition.split(operator)
        require(parts.size == 2) { "Invalid condition format: $condition" }

        val label = parts[0].trim()
        val values = parts[1].split(",").map { it.trim() }

        return Triple(label, operator, values)
    }

    private fun isInRange(range: String, value: String): Boolean {
        val metadataValue = value.toIntOrNull() ?: return false

        return when {
            range.endsWith("xx") -> {
                isWithinBounds(range,100, metadataValue)
            }
            range.endsWith("x") -> {
               isWithinBounds(range, 10, metadataValue)
            }
            else -> false
        }
    }

    private fun isWithinBounds(range: String, multiplier: Int, value: Int): Boolean {
        val bounds = getRangeBounds(range, multiplier)
        return bounds?.contains(value) ?: false
    }

    private fun getRangeBounds(range: String, multiplier: Int): IntRange? {
        val rangeStart = range.dropLast(multiplier.toString().length-1).toIntOrNull()?.times(multiplier)
        return rangeStart?.let { it..<it + (multiplier - 1) }
    }
}