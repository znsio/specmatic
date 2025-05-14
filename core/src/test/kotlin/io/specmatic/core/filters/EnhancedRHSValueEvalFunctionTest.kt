package io.specmatic.core.filters

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnhancedRHSValueEvalFunctionTest {
    @Test
    fun `test status code greater than equal to 500 and less than 500 using filter`() {
        val expression = "STATUS>='500' && STATUS<'510'"
        val evalExExpression = ExpressionStandardizer.filterToEvalEx(expression)
        
        val expressionGET200 = evalExExpression.with("METHOD", "GET").with("STATUS", 200)
        val resultGET200 = expressionGET200.evaluate().value

        val expressionGET500 = evalExExpression.with("METHOD", "GET").with("STATUS", 500)
        val resultGET500 = expressionGET500.evaluate().value

        val expressionGET501 = evalExExpression.with("METHOD", "GET").with("STATUS", 501)
        val resultGET501 = expressionGET501.evaluate().value

        val expressionGET420 = evalExExpression.with("METHOD", "GET").with("STATUS", 420)
        val resultGET420 = expressionGET420.evaluate().value

        assertFalse(resultGET200 as Boolean)
        assertTrue(resultGET500 as Boolean)
        assertTrue(resultGET501 as Boolean)

        assertFalse(resultGET420 as Boolean)
    }

    @Test
    fun `test status code greater than 200 and less than equal to 500 using filter`() {
        val expression = "STATUS>'200' && STATUS<='500'"
        val evalExExpression = ExpressionStandardizer.filterToEvalEx(expression)
        
        val expressionGET200 = evalExExpression.with("METHOD", "GET").with("STATUS", 200)
        val resultGET200 = expressionGET200.evaluate().value

        val expressionGET500 = evalExExpression.with("METHOD", "GET").with("STATUS", 500)
        val resultGET500 = expressionGET500.evaluate().value

        val expressionGET501 = evalExExpression.with("METHOD", "GET").with("STATUS", 501)
        val resultGET501 = expressionGET501.evaluate().value

        val expressionGET420 = evalExExpression.with("METHOD", "GET").with("STATUS", 420)
        val resultGET420 = expressionGET420.evaluate().value

        assertFalse(resultGET200 as Boolean)
        assertTrue(resultGET500 as Boolean)
        assertFalse(resultGET501 as Boolean)
        assertTrue(resultGET420 as Boolean)
    }
}
