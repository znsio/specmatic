package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import io.specmatic.core.Scenario

interface ExpressionContextPopulator {
    fun populateExpressionData(expression: Expression): Expression
}

class ScenarioFilterVariablePopulator(private val scenario: Scenario) : ExpressionContextPopulator {
    override fun populateExpressionData(expression: Expression): Expression {
        val foo = rawMap()
        foo.forEach { (k, v) ->
            expression.with(k, v)
        }

        return expression
    }

    private fun rawMap(): Map<String, String> {
        return mapOf(
            "PARAMETERS.HEADER" to "application/json",
            "PARAMETERS.HEADER.CONTENT-TYPE" to "application/json"
        )
//        return mapOf(
//            METHOD.key to scenario.method,
//            PATH.key to scenario.path,
//            STATUS.key to scenario.status.toString(),
//            HEADERS.key to scenario.httpRequestPattern.getHeaderKeys().joinToString(","),
//            QUERY.key to scenario.httpRequestPattern.getQueryParamKeys().joinToString(","),
//            EXAMPLE_NAME.key to scenario.exampleName.orEmpty(),
//        )
    }
}
