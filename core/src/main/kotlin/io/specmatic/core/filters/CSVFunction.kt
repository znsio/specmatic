package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token

@FunctionParameter(name = "value")
class CSVFunction : AbstractFunction() {
    override fun evaluate(
        expression: Expression, functionToken: Token, vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val inputString = parameterValues[0].stringValue

        val (label, operator, values) = parseCondition(inputString)
        val scenarioValue = expression.dataAccessor.getData(label).value
        val result = when (operator) {
            "=" -> values.any { it == scenarioValue.toString() || (label == ScenarioFilterTags.STATUS_CODE.key && isInRange(it, scenarioValue.toString())) }
            "!=" -> values.all { it != scenarioValue.toString() && (label != ScenarioFilterTags.STATUS_CODE.key || !isInRange(it, scenarioValue.toString())) }
            else -> throw IllegalArgumentException("Unsupported operator: $operator")
        }

        return EvaluationValue.of(result, ExpressionConfiguration.defaultConfiguration())
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
        val intValue = value.toIntOrNull() ?: return false
        return when {
            range.endsWith("xx") -> {
                val rangeStart = range.dropLast(2).toInt() * 100
                val rangeEnd = rangeStart + 99
                intValue in rangeStart..rangeEnd
            }
            range.endsWith("x") -> {
                val rangeStart = range.dropLast(1).toInt() * 10
                val rangeEnd = rangeStart + 9
                intValue in rangeStart..rangeEnd
            }
            else -> false
        }
    }
}