package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token

@FunctionParameter(name = "key")
@FunctionParameter(name = "operator")
@FunctionParameter(name = "value")
class EnhancedRHSValueEvalFunction : AbstractFunction() {
    override fun evaluate(
        expression: Expression, functionToken: Token, vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val inputString = parameterValues[0].stringValue
        val (filterKey, operator, filterValue) = parseCondition(inputString)
        val scenarioValue = expression.dataAccessor.getData(filterKey).value.toString()
        val result = evaluateCondition(operator, filterValue, scenarioValue)
        return EvaluationValue.of(result, ExpressionConfiguration.defaultConfiguration())
    }

    private fun evaluateCondition(operator: String, values: List<String>, scenarioValue: String): Boolean {
        return when (operator) {
            ">" -> values.any { (scenarioValue.toIntOrNull() ?: 0) > (it.toIntOrNull() ?: 0) }
            "<" -> values.any { (scenarioValue.toIntOrNull() ?: 0) < (it.toIntOrNull() ?: 0) }
            ">=" -> values.any { (scenarioValue.toIntOrNull() ?: 0) >= (it.toIntOrNull() ?: 0) }
            "<=" -> values.any { (scenarioValue.toIntOrNull() ?: 0) <= (it.toIntOrNull() ?: 0) }
            else -> throw IllegalArgumentException("Unsupported operator: $operator")
        }
    }


    private fun parseCondition(condition: String): Triple<String, String, List<String>> {
        val operator = when {
            condition.contains("!=") -> "!="
            condition.contains(">=") -> ">="
            condition.contains("<=") -> "<="
            condition.contains(">") -> ">"
            condition.contains("<") -> "<"
            else -> "="
        }
        val parts = condition.split(operator)
        require(parts.size == 2) { "Invalid condition format: $condition" }

        val label = parts[0].trim()
        val values = parts[1].split(",").map { it.trim() }

        return Triple(label, operator, values)
    }

}
