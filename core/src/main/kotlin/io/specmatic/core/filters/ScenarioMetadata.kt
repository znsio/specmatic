package io.specmatic.core.filters

import com.ezylang.evalex.Expression

data class ScenarioMetadata(
    val method: String,
    val path: String,
    val statusCode: Int,
    val header: Set<String>,
    val query: Set<String>,
    val exampleName: String
) : ExpressionContextPopulator {
    override fun populateExpressionData(expression: Expression): Expression {
//        return expression
//            .with(METHOD.key, method)
//            .with(PATH.key, path)
//            .with(STATUS.key, statusCode.toString())
//            .with(HEADERS.key, header.joinToString(","))
//            .with(QUERY.key, query.joinToString(","))
//            .with(EXAMPLE_NAME.key, exampleName)
        return expression
    }
}
