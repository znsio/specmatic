package io.specmatic.core.filters

sealed class Token {
    data class FuncCall(val key: String, val operator: String, val value: String) : Token()
    data class Symbol(val value: String) : Token()
    object And : Token()
    object Or : Token()
    object Not : Token()
    object LParen : Token()
    object RParen : Token()
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
                    while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '_')) i++
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

    private fun tokensToString(tokens: List<Token>): String {
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
}
