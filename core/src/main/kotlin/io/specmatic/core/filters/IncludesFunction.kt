package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import io.specmatic.core.filters.ScenarioFilterTags.*
import java.util.regex.Pattern

@FunctionParameter(name = "key")
@FunctionParameter(name = "possibleValues", isVarArg = true)
class IncludesFunction : AbstractFunction() {
    override fun evaluate(
        expression: Expression, functionToken: Token, vararg parameterValues: EvaluationValue
    ): EvaluationValue? {
        val paramName = parameterValues.first().stringValue
        val possibleValues = parameterValues.drop(1).map { it.stringValue }

        val scenarioValue = expression.dataAccessor.getData(paramName).stringValue


        val result = possibleValues.any {
            fun checkCondition(value: String): Boolean {
                return when (paramName) {
                    STATUS.key -> value == scenarioValue || isInRange(value, scenarioValue)
                    PATH.key -> value == scenarioValue || matchesPath(value, scenarioValue)
                    HEADERS.key -> value == scenarioValue || matchMultipleExpressions(value, scenarioValue)
                    QUERY.key -> value == scenarioValue || matchMultipleExpressions(value, scenarioValue)
                    else -> value == scenarioValue
                }
            }

            checkCondition(it)
        }
        return EvaluationValue.booleanValue(result)
    }


    private fun matchesPath(value: String, scenarioValue: String): Boolean {
        return value.contains("*") && Pattern.compile(
            value.replace("(", "\\(")
                .replace(")", "\\)").replace("*", ".*")
        ).matcher(scenarioValue).matches()
    }

    private fun isInRange(range: String, value: String): Boolean {
        val metadataValue = value.toIntOrNull() ?: return false

        return when {
            range.endsWith("xx") -> isWithinBounds(range, 100, metadataValue)
            range.endsWith("x") -> isWithinBounds(range, 10, metadataValue)
            else -> false
        }
    }

    private fun isWithinBounds(range: String, multiplier: Int, value: Int): Boolean {
        val len = multiplier.toString().length - 1
        val rangeStart = range.dropLast(len).toIntOrNull()?.times(multiplier)
        return rangeStart?.let { value in it until it + multiplier - 1 } ?: false
    }

    private fun matchMultipleExpressions(value: String, scenarioValue: String): Boolean {
        val matchValue = scenarioValue.split(",").map { it.trim().removeSuffix("?") }
        return matchValue.any { it == value }
    }
}
