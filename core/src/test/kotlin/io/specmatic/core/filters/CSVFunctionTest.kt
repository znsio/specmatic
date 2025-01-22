package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CSVFunctionTest {

    @Test
    fun `test CSV function with basic expression`() {
        val evalExExpression = "(METHOD='GET' && CSV('STATUS=200,400'))"
            val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
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
        val evalExExpression = "CSV('METHOD=GET,POST')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
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
        val evalExExpression = "(CSV('METHOD=GET,POST') && CSV('STATUS=200,400'))"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
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
        val evalExExpression = "(CSV('METHOD=GET,POST') && CSV('STATUS=200,400,5xx'))"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
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
        val evalExExpression = "CSV('STATUS=50x')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
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
        val evalExExpression = "CSV('STATUS!=50x')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
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
        val evalExExpression = "CSV('METHOD!=GET,POST')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
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
        val evalExExpression = "CSV('METHOD=GET,POST') || CSV('PATH=/monitor,/monitor(id:string)')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionMonitor = Expression(evalExExpression, configuration).with("METHOD","PATCH").with("PATH", "/monitor")
        val resultMonitor = expressionMonitor.evaluate().value

        val expressionMonitorWithId = Expression(evalExExpression, configuration).with("METHOD","PATCH").with("PATH", "/monitor(id:string)")
        val resultMonitorWithId = expressionMonitorWithId.evaluate().value

        val pathCabin = Expression(evalExExpression, configuration).with("METHOD","").with("PATH", "/cabin")
        val resultCabin = pathCabin.evaluate().value


        assertTrue(resultMonitor as Boolean)
        assertFalse(resultCabin as Boolean)
        assertTrue(resultMonitorWithId as Boolean)
    }

    @Test
    fun `test CSV function with only 1 range`() {
        val evalExExpression = "CSV('STATUS=20x')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val status200 = Expression(evalExExpression, configuration).with("STATUS",200)
        val result200 = status200.evaluate().value

        val status500 = Expression(evalExExpression, configuration).with("STATUS",500)
        val result500 = status500.evaluate().value

        val status201 = Expression(evalExExpression, configuration).with("STATUS",201)
        val result201 = status201.evaluate().value


        assertTrue(result200 as Boolean)
        assertFalse(result500 as Boolean)
        assertTrue(result201 as Boolean)
    }

    @Test
    fun `test CSV function with TMF PATHS`() {
        val evalExExpression ="STATUS!=202 && CSV('PATH!=/hub,/hub/(id:string)')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionFailure = Expression(evalExExpression, configuration).with("STATUS",202)
        val resultFailure = expressionFailure.evaluate().value

        val expressionFailureWithPath = Expression(evalExExpression, configuration).with("STATUS",202).with("PATH","/hub")
        val resultFailureWithPath = expressionFailureWithPath.evaluate().value

        val expressionCorrect = Expression(evalExExpression, configuration).with("STATUS",201).with("PATH","/hello")
        val resultCorrect = expressionCorrect.evaluate().value


        assertFalse(resultFailure as Boolean)
        assertFalse(resultFailureWithPath as Boolean)
        assertTrue(resultCorrect as Boolean)
    }

    @Test
    fun `test CSV function relative path`() {
        val evalExExpression ="CSV('PATH=/products/*/v1')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionCorrect = Expression(evalExExpression, configuration).with("PATH","/products/car/v1")
        val resultCorrect = expressionCorrect.evaluate().value

        val expressionFailure = Expression(evalExExpression, configuration).with("PATH","/products/v2/car/v2")
        val resultFailure = expressionFailure.evaluate().value

        assertTrue(resultCorrect as Boolean)
        assertFalse(resultFailure as Boolean)
    }
    @Test
    fun `test CSV function relative path fail`() {
        val evalExExpression ="CSV('PATH!=/products/*/v1')"
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionCorrect = Expression(evalExExpression, configuration).with("PATH","/products/car")
        val resultCorrect = expressionCorrect.evaluate().value

        val expressionFailure = Expression(evalExExpression, configuration).with("PATH","/products/car/v1")
        val resultFailure = expressionFailure.evaluate().value

        assertTrue(resultCorrect as Boolean)
        assertFalse(resultFailure as Boolean)
    }

    @Test
    fun `test CSV function with empty string`() {
        val evalExExpression =""
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
            .withAdditionalFunctions(
                mapOf(Pair("CSV", CSVFunction())).entries.single()
            )

        val expressionFailure = Expression(evalExExpression, configuration).with("PATH","/products/car/v1")

       assertThrows<Exception>(){
           expressionFailure.evaluate().value
       }
    }

    @Test
    fun `test CSV function with QUERY`() {
        val configuration = ExpressionConfiguration.builder()
            .singleQuoteStringLiteralsAllowed(true).build()
        val evalExExpression ="QUERY='fields'"
        val expression = Expression(evalExExpression,configuration).with("QUERY","fields").evaluate().value
        assertTrue(expression as Boolean)
    }
}
