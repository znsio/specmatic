package io.specmatic.core.filters

object FilterSymbols {
    enum class LogicalOperator(val symbol: String) {
        AND("&&"),
        OR("||"),
        NOT("!");

        companion object {
            fun fromSymbol(symbol: String): LogicalOperator? =
                entries.find { it.symbol == symbol }

            fun contains(symbol: String): Boolean =
                entries.find { it.symbol == symbol } != null
        }

    }

    enum class Parenthesis(val symbol: String) {
        OPEN("("),
        CLOSE(")");

        companion object {
            fun fromSymbol(symbol: String): Parenthesis? =
                values().find { it.symbol == symbol }
        }
    }

    enum class ComparisonOperator(val symbol: String) {
        EQUAL("="),
        NOT_EQUAL("!=");

        companion object {
            fun fromSymbol(symbol: String): ComparisonOperator? =
                values().find { it.symbol == symbol }
        }
    }

    enum class SpecialSymbol(val symbol: String) {
        WILDCARD("*"),
        RANGE("xx");

        companion object {
            fun isRange(value: String): Boolean =
                value.matches(Regex("\\dxx"))

            fun fromSymbol(symbol: String): SpecialSymbol? =
                when (symbol) {
                    "*" -> WILDCARD
                    else -> null
                }
        }
    }
}
