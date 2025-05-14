package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IncludesFunctionTest {

    @Test
    fun `should answer true if value in context is part of the given list`() {
        val configuration = ExpressionConfiguration.builder().singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(mapOf(Pair("includes", IncludesFunction())).entries.single())
        val expression = Expression("includes('PARAMETERS.STATUS', '1', '2', '3')", configuration)
        expression.with("PARAMETERS.STATUS", "2")
        val evaluationValue = expression.evaluate()
        assertThat(evaluationValue.booleanValue).isTrue()
    }

    @Test
    fun `should answer false if value in context is part of the given list`() {
        val configuration = ExpressionConfiguration.builder().singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(mapOf(Pair("includes", IncludesFunction())).entries.single())
        val expression = Expression("includes('PARAMETERS.STATUS', '1', '2', '3')", configuration)
        expression.with("PARAMETERS.STATUS", "200")
        val evaluationValue = expression.evaluate()
        assertThat(evaluationValue.booleanValue).isFalse()
    }

}
