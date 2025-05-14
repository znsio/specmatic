package io.specmatic.core.filters

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