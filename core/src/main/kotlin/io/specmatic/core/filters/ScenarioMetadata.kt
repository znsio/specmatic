package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import io.specmatic.core.pattern.Examples

data class ScenarioMetadata(
    val method: String,
    val path: String,
    val statusCode: Int,
    val header: Set<String>,
    val query: Set<String>,
    var exampleName: String,
    var examples: List<Examples> = emptyList(),
) {
    fun populateExpressionData(expression: Expression): Expression {
        if (exampleName.isEmpty() && expression.expressionString.contains(ScenarioFilterTags.EXAMPLE_NAME.key)) {
            val matchingExamples = examples.filter { example ->
                example.rows.any { row -> expression.expressionString.contains(row.name) }
            }.map { example ->
                example.copy(rows = example.rows.filter { row ->
                    expression.expressionString.contains(row.name)
                })
            }

            if (matchingExamples.isNotEmpty()) {
                examples = matchingExamples
                exampleName = matchingExamples.firstOrNull()?.rows?.firstOrNull()?.name ?: ""
            }
        }

        return expression
            .with(ScenarioFilterTags.METHOD.key, method)
            .with(ScenarioFilterTags.PATH.key, path)
            .with(ScenarioFilterTags.STATUS_CODE.key, statusCode.toString())
            .with(ScenarioFilterTags.HEADER.key, header.joinToString(","))
            .with(ScenarioFilterTags.QUERY.name, query.joinToString(","))
            .with(ScenarioFilterTags.EXAMPLE_NAME.key, exampleName)
    }
}