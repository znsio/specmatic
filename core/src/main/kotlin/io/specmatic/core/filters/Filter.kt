package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import io.specmatic.core.Scenario
import io.specmatic.test.TestResultRecord

data class Filter<T>(
    val expression: Expression? = null
) {
    fun isSatisfiedBy(metadata: T): Boolean {
        var expression = expression ?: return true

        val variables = when (metadata) {
            is ScenarioMetadata -> {
                mapOf(
                    ScenarioFilterTags.METHOD.key to metadata.method,
                    ScenarioFilterTags.PATH.key to metadata.path,
                    ScenarioFilterTags.STATUS_CODE.key to metadata.statusCode.toString(),
                    ScenarioFilterTags.HEADER.key to metadata.header.joinToString(","),
                    ScenarioFilterTags.QUERY.name to metadata.query.joinToString(","),
                    ScenarioFilterTags.EXAMPLE_NAME.name to metadata.exampleName
                )
            }
            is TestResultRecord -> {
                mapOf(
                    ScenarioFilterTags.PATH.key to metadata.path
                )
            }
            else -> throw IllegalArgumentException("Unsupported metadata type")
        }

        variables.forEach { (key, value) ->
            expression = expression.with(key, value)
        }
        return try {
            expression.evaluate().booleanValue ?: false
        } catch (e: Exception) {
            val errorMsg = "Error in filter expression: ${e.message?.replace("brace", "bracket")}\n"
            print(errorMsg)
            throw IllegalArgumentException(errorMsg)
        }
    }

    companion object {
        const val ENHANCED_FUNC_NAME = "eFunc"

        fun from(filterExpression: String): Filter<Any> {
            if (filterExpression.isBlank()) return Filter()

            val evalExExpression = standardizeExpression(filterExpression)
            val configuration = ExpressionConfiguration.builder()
                .singleQuoteStringLiteralsAllowed(true)
                .build()
                .withAdditionalFunctions(
                    mapOf(Pair(ENHANCED_FUNC_NAME, EnhancedRHSValueEvalFunction())).entries.single()
                )
            val finalExpression = Expression(evalExExpression, configuration)
            return Filter(expression = finalExpression)
        }

        fun standardizeExpression(expression: String): String {
            val regexPattern = "\\b\\w+(=|!=)('[^']*([,x*])[^']*')".trimIndent().toRegex()

            return regexPattern.replace(expression) { matchResult ->
                "$ENHANCED_FUNC_NAME('${matchResult.value.filter { it != '\'' }}')"
            }
        }

        fun <T> filterUsing(
            items: Sequence<T>,
            filter: Filter<Any>,
            toMetadata: (T) -> Any
        ): Sequence<T> {
            return items.filter { item ->
                when (val metadata = toMetadata(item)) {
                    is ScenarioMetadata -> filter.isSatisfiedBy(metadata)
                    is TestResultRecord -> filter.isSatisfiedBy(metadata)
                    else -> throw IllegalArgumentException("Unsupported metadata type")
                }
            }
        }
    }
}