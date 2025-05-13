package io.specmatic.core.filters

sealed class Token {
    data class Operation(val key: String, val operator: Operator, val value: String) : Token()
    data class Symbol(val value: String) : Token()
    object And : Token()
    object Or : Token()
    object Not : Token()
    object LParen : Token()
    object RParen : Token()
}

abstract class Operator(val symbol: String) {
    abstract fun operate(left: String, right: String): String

    protected fun String.assertSingleValue() {
        if (contains(",")) {
            throw IllegalArgumentException("Operator '${symbol}' does not support multiple values. You specified '${this}'")
        }
    }

    protected fun String.splitToQuoteValues(): String {
        return split(',').joinToString(", ") { "'${it.trim()}'" }
    }

    private class LessThanEqualsOperator : Operator("<=") {
        override fun operate(left: String, right: String): String {
            right.assertSingleValue()
            return "eFunc('$left', '$symbol', '$right')"
        }

    }

    private class GreaterThanEqualsOperator : Operator(">=") {
        override fun operate(left: String, right: String): String {
            right.assertSingleValue()
            return "eFunc('$left', '$symbol', '$right')"
        }
    }

    private class NotEqualsOperator : Operator("!=") {
        override fun operate(left: String, right: String): String {
            val rightValues = right.splitToQuoteValues()
            return "!includes('$left', $rightValues)"
        }

    }

    private class LessThanOperator : Operator("<") {
        override fun operate(left: String, right: String): String {
            right.assertSingleValue()
            return "eFunc('$left', '$symbol', '$right')"
        }
    }

    private class GreaterThanOperator : Operator(">") {
        override fun operate(left: String, right: String): String {
            right.assertSingleValue()
            return "eFunc('$left', '$symbol', '$right')"
        }
    }

    private class EqualsOperator() : Operator("=") {
        override fun operate(left: String, right: String): String {
            val rightValues = right.splitToQuoteValues()
            return "includes('$left', $rightValues)"
        }
    }

    companion object {
        val ALL = listOf(
            NotEqualsOperator(),
            GreaterThanEqualsOperator(),
            LessThanEqualsOperator(),
            EqualsOperator(),
            GreaterThanOperator(),
            LessThanOperator(),
        ).sortedByDescending { it.symbol.length }
    }
}


class ExpressionStandardizer {

    fun tokenizeExpression(expression: String): String {
        val tokens = tokenize(expression)
        return tokensToString(tokens)
    }

    private fun tokenize(expr: String): List<Token> {
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
                    while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '_' || expr[i] == '.' || expr[i] == '-')) i++
                    val key = expr.substring(keyStart, i)

                    skipWhitespace()

                    val operator = Operator.ALL.find { eachOperator -> expr.startsWith(eachOperator.symbol, i) }
                        ?: throw IllegalArgumentException("Expected an operator for the given expression: $expr at position $i")

                    i += operator.symbol.length

                    skipWhitespace()

                    if (expr[i] != '\'') throw IllegalArgumentException("Expected quote for the given expression: $expr at position $i")

                    i++ // skip opening quote
                    val valueStart = i
                    while (i < expr.length && expr[i] != '\'') i++
                    val value = expr.substring(valueStart, i)
                    i++ // skip closing quote

                    tokens.add(Token.Operation(key, operator, value))
                }
            }
        }

        return tokens
    }

    private fun tokensToString(tokens: List<Token>): String {
        return tokens.joinToString(" ") { token ->
            when (token) {
                is Token.Operation -> token.operator.operate(token.key, token.value)
                is Token.And -> "&&"
                is Token.Or -> "||"
                is Token.Not -> "!"
                is Token.LParen -> "("
                is Token.RParen -> ")"
                is Token.Symbol -> token.value
            }
        }
    }
}
