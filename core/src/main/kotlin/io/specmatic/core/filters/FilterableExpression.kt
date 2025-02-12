package io.specmatic.core.filters

import com.ezylang.evalex.Expression

interface FilterableExpression {
    fun populateExpressionData(expression: Expression): Expression
}