package io.specmatic.core.filters

import com.ezylang.evalex.EvaluationException
import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.parser.ParseException

class CustomExpression(
    expressionString: String,
    configuration: ExpressionConfiguration = ExpressionConfiguration.defaultConfiguration()
) : Expression(expressionString, configuration) {

    private var key: String? = null
    private var value: Any? = null

    override fun with(key: String?, value: Any): Expression {
        this.key = key
        this.value = value
        return super.with(key, value)
    }

    @Throws(EvaluationException::class, ParseException::class)
    override fun evaluate(): EvaluationValue {
        val standardizedExpression = standardizeExpression(expressionString)
        return Expression(standardizedExpression).evaluate()
    }

    private fun standardizeExpression(expression: String): String {
        var standardized = expression
        standardized = escapePaths(standardized)
        standardized = convertEqualsToDoubleEquals(standardized)
        standardized = encloseValues(standardized)
        standardized =  handleMultipleValues(standardized)
        return handleStatusRange(standardized)
    }

    private fun escapePaths(expression: String): String {
        return expression.split(" ").joinToString(" ") { part ->
            if (part.startsWith("/") && !part.startsWith("\"")) "\"$part\"" else part
        }
    }

    private fun convertEqualsToDoubleEquals(expression: String): String {
        return expression.replace("=", "==")
    }

    private fun encloseValues(expression: String): String {
        val regex = Regex("""(\b\w+\b)(==|!=)([^()&|]+)""")
        return regex.replace(expression) { match ->
            val key = match.groupValues[1]
            val operator = match.groupValues[2]
            val value = match.groupValues[3].trim()
            "$key$operator\"$value\""
        }
    }

    private fun handleMultipleValues(expression: String): String {
        val statusRegex = Regex("""STATUS(==|!=)"([0-9,]+)"""")
        return statusRegex.replace(expression) { match ->
            val operator = match.groupValues[1]
            val values = match.groupValues[2].split(",")
            val newCondition = values.joinToString(" ${if (operator == "==") "||" else "&&"} ") {
                "STATUS$operator\"$it\""
            }
            "($newCondition)"
        }
    }

    private fun handleStatusRange(expression: String): String {
        return expression.split("==", "!=").joinToString(" ") { part ->
            if (part.contains("x")) {
                val prefix = part.substringBefore("x").trim()
                val statusRange = (0..9).map { "$prefix${it.toString().padStart(1, '0')}" }
                "(${statusRange.joinToString(" ${if (expression.contains("==")) "||" else "&&"} ") { "STATUS${expression.substringBefore(part)}\"$it\"" }})"
            } else {
                part
            }
        }
    }

}
