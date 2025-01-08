package io.specmatic.core.filters

object FilterSymbols {
    enum class LogicalOperator(val symbol: String) {
        AND("&&"),
        OR("||"),
        NOT("!");

        companion object {
            fun contains(symbol: String): Boolean =
                entries.find { it.symbol == symbol } != null
        }

    }

    enum class Parenthesis(val symbol: String) {
        OPEN("("),
        CLOSE(")");

    }

    enum class ComparisonOperator(val symbol: String) {
        EQUAL("="),
        NOT_EQUAL("!=");

    }

    enum class SpecialSymbol(val symbol: String) {
        WILDCARD("*"),
        RANGE("xx");

    }
}
