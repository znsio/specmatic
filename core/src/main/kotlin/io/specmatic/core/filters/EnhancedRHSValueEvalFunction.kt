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
        val (filterKey, operator, filterValue) = Triple(
            parameterValues[0].stringValue,
            parameterValues[1].stringValue,
            parameterValues[2].stringValue.split(",").map { it.trim() }
        )
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

}
