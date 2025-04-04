package io.specmatic.core.filters

import com.ezylang.evalex.Expression

data class ScenarioMetadata(
    val method: String,
    val path: String,
    val statusCode: Int,
    val header: Set<String>,
    val query: Set<String>,
    val exampleName: String
) {
    fun populateExpressionData(expression: Expression): Expression {
        return expression
            .with(ScenarioFilterTags.METHOD.key, method)
            .with(ScenarioFilterTags.PATH.key, path)
            .with(ScenarioFilterTags.STATUS_CODE.key, statusCode.toString())
            .with(ScenarioFilterTags.HEADER.key, header.joinToString(","))
            .with(ScenarioFilterTags.QUERY.name, query.joinToString(","))
            .with(ScenarioFilterTags.EXAMPLE_NAME.name, exampleName)
    }
}