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

        sealed class Token {
            data class FuncCall(val key: String, val operator: String, val value: String) : Token()
            data class Symbol(val value: String) : Token()
            object And : Token()
            object Or : Token()
            object Not : Token()
            object LParen : Token()
            object RParen : Token()
        }

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
            fun tokenize(expr: String): List<Token> {
                val tokens = mutableListOf<Token>()
                var i = 0

                fun skipWhitespace() {
                    while (i < expr.length && expr[i].isWhitespace()) i++
                }

                while (i < expr.length) {
                    skipWhitespace()

                    when {
                        expr.startsWith("&&", i) -> {
                            tokens.add(Token.And)
                            i += 2
                        }
                        expr.startsWith("||", i) -> {
                            tokens.add(Token.Or)
                            i += 2
                        }
                        expr[i] == '!' -> {
                            tokens.add(Token.Not)
                            i++
                        }
                        expr[i] == '(' -> {
                            tokens.add(Token.LParen)
                            i++
                        }
                        expr[i] == ')' -> {
                            tokens.add(Token.RParen)
                            i++
                        }
                        else -> {
                            // Parse a key[=|!=]'value' pair
                            val keyStart = i
                            while (i < expr.length && expr[i].isLetterOrDigit() || expr[i] == '_') i++
                            val key = expr.substring(keyStart, i)

                            skipWhitespace()

                            val operator = when {
                                expr.startsWith("!=", i) -> {
                                    i += 2
                                    "!="
                                }
                                expr.startsWith("<=", i) -> {
                                    i += 2
                                    "<="
                                }
                                expr.startsWith(">=", i) -> {
                                    i += 2
                                    ">="
                                }
                                expr.startsWith("~", i) -> {
                                    i += 1
                                    "~"
                                }
                                expr.startsWith("<", i) -> {
                                    i += 1
                                    "<"
                                }
                                expr.startsWith(">", i) -> {
                                    i += 1
                                    ">"
                                }
                                expr.startsWith("=", i) -> {
                                    i += 1
                                    "="
                                }
                                else -> throw IllegalArgumentException("Expected an operator for the given expression: $expr at position $i")
                            }

                            skipWhitespace()

                            if (expr[i] != '\'') throw IllegalArgumentException("Expected quote for the given expression: $expr at position $i")

                            i++ // skip opening quote
                            val valueStart = i
                            while (i < expr.length && expr[i] != '\'') i++
                            val value = expr.substring(valueStart, i)
                            i++ // skip closing quote

                            tokens.add(Token.FuncCall(key, operator, value))
                        }
                    }
                }

                return tokens
            }

            fun tokensToString(tokens: List<Token>): String {
                return tokens.joinToString(" ") { token ->
                    when (token) {
                        is Token.FuncCall -> "eFunc('${token.key}${token.operator}${token.value}')"
                        is Token.And -> "&&"
                        is Token.Or -> "||"
                        is Token.Not -> "!"
                        is Token.LParen -> "("
                        is Token.RParen -> ")"
                        is Token.Symbol -> token.value
                    }
                }
            }

            return tokensToString(tokenize(expression))
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