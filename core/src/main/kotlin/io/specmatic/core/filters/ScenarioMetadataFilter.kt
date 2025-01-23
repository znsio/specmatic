package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration

data class ScenarioMetadataFilter(
    val expression: Expression? = null
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        val expression = expression ?: return false
        val expressionWithVariables = expression
            .with(ScenarioFilterTags.METHOD.key, metadata.method)
            .with(ScenarioFilterTags.PATH.key, metadata.path)
            .with(ScenarioFilterTags.STATUS_CODE.key, metadata.statusCode)
            .with(ScenarioFilterTags.HEADER.key, metadata.header.joinToString(","))
            .with(ScenarioFilterTags.QUERY.name, metadata.query.joinToString(","))
            .with(ScenarioFilterTags.EXAMPLE_NAME.name, metadata.exampleName)

        return try {
            expressionWithVariables.evaluate().booleanValue ?: false
        } catch (e: Exception) {
          throw Exception(e)
        }
    }

    companion object {
        fun from(filterExpression: String): ScenarioMetadataFilter {
            val evalExExpression = CSVFunctionExpressionModifier().standardizeExpression(filterExpression)
            val configuration = ExpressionConfiguration.builder()
                .singleQuoteStringLiteralsAllowed(true).build()
                .withAdditionalFunctions(
                    mapOf(Pair("CSV", CSVFunction())).entries.single()
                )
            val finalExpression = Expression(evalExExpression, configuration)
            return ScenarioMetadataFilter(expression = finalExpression)
        }

        fun <T> filterUsing(
            items: Sequence<T>,
            scenarioMetadataFilter: ScenarioMetadataFilter,
            toScenarioMetadata: (T) -> ScenarioMetadata
        ): Sequence<T> {
            val returnItems = items.filter {
                scenarioMetadataFilter.isSatisfiedBy(toScenarioMetadata(it))
            }
            return returnItems
        }
    }
}







