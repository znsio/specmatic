package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration

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