package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration

data class ScenarioMetadataFilter(
    val expression: Expression? = null
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        val expression = expression ?: return true
        val expressionWithVariables = expression
            .with(ScenarioFilterTags.METHOD.key, metadata.method)
            .with(ScenarioFilterTags.PATH.key, metadata.path)
            .with(ScenarioFilterTags.STATUS_CODE.key, metadata.statusCode)
            .with(ScenarioFilterTags.HEADER.key, metadata.header.joinToString(","))
            .with(ScenarioFilterTags.QUERY.name, metadata.query.joinToString(","))
            .with(ScenarioFilterTags.EXAMPLE_NAME.name, metadata.exampleName)

        return expressionWithVariables.evaluate().booleanValue ?: false
    }

    companion object {
        const val ENHANCED_FUNC_NAME = "eFunc"

        fun from(filterExpression: String): ScenarioMetadataFilter {
            if (filterExpression.isBlank()) return ScenarioMetadataFilter()
            val evalExExpression = standardizeExpression(filterExpression)
            val configuration = ExpressionConfiguration.builder()
                .singleQuoteStringLiteralsAllowed(true).build()
                .withAdditionalFunctions(
                    mapOf(Pair(ENHANCED_FUNC_NAME, EnhancedRHSValueEvalFunction())).entries.single()
                )
            val finalExpression = Expression(evalExExpression, configuration)
            return ScenarioMetadataFilter(expression = finalExpression)
        }

        fun standardizeExpression(expression: String): String {
            val regexPattern = "\\b\\w+(=|!=)('[^']*([,x*])[^']*')".trimIndent().toRegex()

            return regexPattern.replace(expression) { matchResult ->
                "$ENHANCED_FUNC_NAME('${matchResult.value.filter { it != '\'' }}')"
            }
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