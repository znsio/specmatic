package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import java.util.Map.entry

data class ScenarioMetadataFilter(
    val expression: Expression? = null
) {
    fun isSatisfiedBy(scenarioMetaData: ScenarioMetadata): Boolean {
        val expression = expression ?: return true

        val expressionWithVariables = scenarioMetaData.populateExpressionData(expression)

        return try {
            expressionWithVariables.evaluate().booleanValue ?: false
        } catch (e: Exception) {
            val errorMsg = "Error in filter expression: ${e.message?.replace("brace", "bracket")}\n"
            print(errorMsg)
            throw IllegalArgumentException(errorMsg)
        }
    }


    companion object {
        const val ENHANCED_FUNC_NAME = "eFunc"
        const val INCLUDES_FUNC_NAME = "includes"

        fun from(filterExpression: String): ScenarioMetadataFilter {
            if (filterExpression.isBlank()) return ScenarioMetadataFilter()
            val evalExExpression = standardizeExpression(filterExpression)
            val functions = mapOf(
                ENHANCED_FUNC_NAME to EnhancedRHSValueEvalFunction(),
                INCLUDES_FUNC_NAME to IncludesFunction()
            )

            val configuration = ExpressionConfiguration.builder()
                .singleQuoteStringLiteralsAllowed(true).build()
                .withAdditionalFunctions(*functions.map { entry(it.key, it.value) }.toTypedArray())

            val finalExpression = Expression(evalExExpression, configuration)
            return ScenarioMetadataFilter(expression = finalExpression)
        }

        fun standardizeExpression(expression: String): String {
            val expressionStandardizer = ExpressionStandardizer()
            return expressionStandardizer.tokenizeExpression(expression)
        }

        fun <T : HasScenarioMetadata> filterUsing(
            items: Sequence<T>,
            scenarioMetadataFilter: ScenarioMetadataFilter
        ): Sequence<T> {
            val filteredItems = items.filter { item ->
                scenarioMetadataFilter.isSatisfiedBy(item.toScenarioMetadata())
            }
            return filteredItems
        }
    }
}
