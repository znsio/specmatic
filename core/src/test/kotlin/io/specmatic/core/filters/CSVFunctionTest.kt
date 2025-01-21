package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CSVFunctionTest {

    @Test
    fun `test CSV function with basic expression`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "(METHOD=\"GET\" && CSV(\"STATUS=200,400\"))"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionGET200 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 200)
        val resultExpressionGET200 = expressionGET200.evaluate().value

        val expressionGET405 =  Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 405)
        val resultExpressionGET405 = expressionGET405.evaluate().value

        assertTrue(resultExpressionGET200 as Boolean)
        assertFalse(resultExpressionGET405 as Boolean)
    }

    @Test
    fun `test CSV function with multiple METHODS`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "CSV(\"METHOD=GET,POST\")"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionGET = Expression(evalExExpression, configuration).with("METHOD", "GET")
        val resultGET = expressionGET.evaluate().value

        val expressionPATCH = Expression(evalExExpression, configuration).with("METHOD", "PATCH")
        val resultPATCH = expressionPATCH.evaluate().value

        assertTrue(resultGET as Boolean)
        assertFalse(resultPATCH as Boolean)
    }

    @Test
    fun `test CSV function with multiple METHODS and STATUS CODE`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "(CSV(\"METHOD=GET,POST\") && CSV(\"STATUS=200,400\"))"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionGET200 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 200)
        val resultGET200 = expressionGET200.evaluate().value


        val expressionGET500 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 500)
        val resultGET500 = expressionGET500.evaluate().value

        val expressionPOST200 = Expression(evalExExpression, configuration).with("METHOD", "POST").with("STATUS", 200)
        val resultPOST200 = expressionPOST200.evaluate().value

        assertTrue(resultGET200 as Boolean)
        assertFalse(resultGET500 as Boolean)
        assertTrue(resultPOST200 as Boolean)

    }

    @Test
    fun `test CSV function STATUS in double digit range`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "(CSV(\"METHOD=GET,POST\") && CSV(\"STATUS=200,400,5xx\"))"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionGET200 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 200)
        val resultGET200 = expressionGET200.evaluate().value


        val expressionGET500 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 500)
        val resultGET500 = expressionGET500.evaluate().value

        val expressionGET501 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 501)
        val resultGET501 = expressionGET501.evaluate().value

        val expressionGET420 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 420)
        val resultGET420 = expressionGET420.evaluate().value

        assertTrue(resultGET200 as Boolean)
        assertTrue(resultGET500 as Boolean)
        assertTrue(resultGET501 as Boolean)

        assertFalse(resultGET420 as Boolean)
    }

    @Test
    fun `test CSV function STATUS in single digit range`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "CSV(\"STATUS=50x\")"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionGET200 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 200)
        val resultGET200 = expressionGET200.evaluate().value


        val expressionGET500 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 500)
        val resultGET500 = expressionGET500.evaluate().value

        val expressionGET501 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 501)
        val resultGET501 = expressionGET501.evaluate().value

        val expressionGET420 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 420)
        val resultGET420 = expressionGET420.evaluate().value

        assertFalse(resultGET200 as Boolean)
        assertTrue(resultGET500 as Boolean)
        assertTrue(resultGET501 as Boolean)

        assertFalse(resultGET420 as Boolean)

    }

    @Test
    fun `test CSV function STATUS not in single digit range`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "CSV(\"STATUS!=50x\")"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionGET200 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 200)
        val resultGET200 = expressionGET200.evaluate().value


        val expressionGET500 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 500)
        val resultGET500 = expressionGET500.evaluate().value

        val expressionGET501 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 501)
        val resultGET501 = expressionGET501.evaluate().value

        val expressionGET420 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 420)
        val resultGET420 = expressionGET420.evaluate().value

        assertTrue(resultGET200 as Boolean)
        assertFalse(resultGET500 as Boolean)
        assertFalse(resultGET501 as Boolean)

        assertTrue(resultGET420 as Boolean)

    }

    @Test
    fun `test CSV function METHOD not in GET and POST`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "CSV(\"METHOD!=GET,POST\")"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionGET200 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 200)
        val resultGET200 = expressionGET200.evaluate().value


        val expressionGET500 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 500)
        val resultGET500 = expressionGET500.evaluate().value

        val expressionGET501 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 501)
        val resultGET501 = expressionGET501.evaluate().value

        val expressionGET420 = Expression(evalExExpression, configuration).with("METHOD", "PATCH").with("STATUS", 420)
        val resultGET420 = expressionGET420.evaluate().value

        assertFalse(resultGET200 as Boolean)
        assertFalse(resultGET500 as Boolean)
        assertFalse(resultGET501 as Boolean)

        assertTrue(resultGET420 as Boolean)

    }

    @Test
    fun `test CSV function with METHODS and PATHS`() {
        // Old Expression : "METHOD=GET && STATUS=200,400"
        val evalExExpression = "CSV(\"METHOD=GET,POST\") || CSV(\"PATH=/monitor,/cabinet\")"
        val configuration = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionMonitor = Expression(evalExExpression, configuration).with("METHOD","PATCH").with("PATH", "/monitor")
        val resultMonitor = expressionMonitor.evaluate().value

        val pathCabin = Expression(evalExExpression, configuration).with("METHOD","").with("PATH", "/cabin")
        val resultCabin = pathCabin.evaluate().value

        assertTrue(resultMonitor as Boolean)
        assertFalse(resultCabin as Boolean)
    }
}
