package io.specmatic.core.filters

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.regex.Pattern

private const val FUNC = "FUNCTION_NAME"

class EnhancedRHSValueEvalFunctionTest {
    private val configuration: ExpressionConfiguration = ExpressionConfiguration.builder()
        .singleQuoteStringLiteralsAllowed(true).build()
        .withAdditionalFunctions(
            mapOf(Pair(FUNC, EnhancedRHSValueEvalFunction())).entries.single()
        )
    
    @Test
    fun `should escape special char in regex pattern match`() {
        val value = "/monitor(id:string)"
        val scenarioValue = "/monitor(id:string)"
        assertTrue(Pattern.compile(value.replace("(", "\\(")
            .replace(")", "\\)")
            .replace("*", ".*")).matcher(scenarioValue).matches())
    }

    @Test
    fun `test custom function with basic expression`() {
        val evalExExpression = "(METHOD='GET' && $FUNC('STATUS=200,400'))"
        
        val expressionGET200 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 200)
        val resultExpressionGET200 = expressionGET200.evaluate().value

        val expressionGET405 = Expression(evalExExpression, configuration).with("METHOD", "GET").with("STATUS", 405)
        val resultExpressionGET405 = expressionGET405.evaluate().value

        assertTrue(resultExpressionGET200 as Boolean)
        assertFalse(resultExpressionGET405 as Boolean)
    }

    @Test
    fun `test custom function with multiple METHODS`() {
        val evalExExpression = "$FUNC('METHOD=GET,POST')"

        val expressionGET = Expression(evalExExpression, configuration).with("METHOD", "GET")
        val resultGET = expressionGET.evaluate().value

        val expressionPATCH = Expression(evalExExpression, configuration).with("METHOD", "PATCH")
        val resultPATCH = expressionPATCH.evaluate().value

        assertTrue(resultGET as Boolean)
        assertFalse(resultPATCH as Boolean)
    }

    @Test
    fun `test custom function with multiple METHODS and STATUS CODE`() {
        val evalExExpression = "($FUNC('METHOD=GET,POST') && $FUNC('STATUS=200,400'))"
        
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
    fun `test custom function STATUS in double digit range`() {
        val evalExExpression = "($FUNC('METHOD=GET,POST') && $FUNC('STATUS=200,400,5xx'))"
        
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
    fun `test custom function STATUS in single digit range`() {
        val evalExExpression = "$FUNC('STATUS=50x')"
        
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
    fun `test custom function STATUS not in single digit range`() {
        val evalExExpression = "$FUNC('STATUS!=50x')"
        
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
    fun `test custom function METHOD not in GET and POST`() {
        val evalExExpression = "$FUNC('METHOD!=GET,POST')"
        
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
    fun `test custom function with METHODS and PATHS`() {
        val evalExExpression = "$FUNC('METHOD=GET,POST') || $FUNC('PATH=/monitor,/monitor(id:string)')"
        
        val expressionMonitor = Expression(evalExExpression, configuration).with("METHOD", "PATCH").with("PATH", "/monitor")
        val resultMonitor = expressionMonitor.evaluate().value

        val expressionMonitorWithId = Expression(evalExExpression, configuration).with("METHOD", "PATCH").with("PATH", "/monitor(id:string)")
        val resultMonitorWithId = expressionMonitorWithId.evaluate().value

        val pathCabin = Expression(evalExExpression, configuration).with("METHOD", "").with("PATH", "/cabin")
        val resultCabin = pathCabin.evaluate().value

        assertTrue(resultMonitor as Boolean)
        assertFalse(resultCabin as Boolean)
        assertTrue(resultMonitorWithId as Boolean)
    }

    @Test
    fun `test custom function with only 1 range`() {
        val evalExExpression = "$FUNC('STATUS=20x')"

        val status200 = Expression(evalExExpression, configuration).with("STATUS", 200)
        val result200 = status200.evaluate().value

        val status500 = Expression(evalExExpression, configuration).with("STATUS", 500)
        val result500 = status500.evaluate().value

        val status201 = Expression(evalExExpression, configuration).with("STATUS", 201)
        val result201 = status201.evaluate().value

        assertTrue(result200 as Boolean)
        assertFalse(result500 as Boolean)
        assertTrue(result201 as Boolean)
    }

    @Test
    fun `test custom function with TMF PATHS`() {
        val evalExExpression = "STATUS!=202 && $FUNC('PATH!=/hub,/hub/(id:string)')"

        val expressionFailure = Expression(evalExExpression, configuration).with("STATUS", 202)
        val resultFailure = expressionFailure.evaluate().value

        val expressionFailureWithPath =
            Expression(evalExExpression, configuration).with("STATUS", 202).with("PATH", "/hub")
        val resultFailureWithPath = expressionFailureWithPath.evaluate().value

        val expressionCorrect = Expression(evalExExpression, configuration).with("STATUS", 201).with("PATH", "/hello")
        val resultCorrect = expressionCorrect.evaluate().value

        assertFalse(resultFailure as Boolean)
        assertFalse(resultFailureWithPath as Boolean)
        assertTrue(resultCorrect as Boolean)
    }

    @Test
    fun `test custom function relative path`() {
        val evalExExpression = "$FUNC('PATH=/products/*/v1')"

        val expressionCorrect = Expression(evalExExpression, configuration).with("PATH", "/products/car/v1")
        val resultCorrect = expressionCorrect.evaluate().value

        val expressionFailure = Expression(evalExExpression, configuration).with("PATH", "/products/v2/car/v2")
        val resultFailure = expressionFailure.evaluate().value

        assertTrue(resultCorrect as Boolean)
        assertFalse(resultFailure as Boolean)
    }

    @Test
    fun `test custom function relative path fail`() {
        val evalExExpression = "$FUNC('PATH!=/products/*/v1')"
        
        val expressionCorrect = Expression(evalExExpression, configuration).with("PATH", "/products/car")
        val resultCorrect = expressionCorrect.evaluate().value

        val expressionFailure = Expression(evalExExpression, configuration).with("PATH", "/products/car/v1")
        val resultFailure = expressionFailure.evaluate().value

        assertTrue(resultCorrect as Boolean)
        assertFalse(resultFailure as Boolean)
    }

    @Test
    fun `test custom function with empty string`() {
        val evalExExpression = ""
        val expressionFailure = Expression(evalExExpression, configuration).with("PATH", "/products/car/v1")

        assertThrows<Exception> {
            expressionFailure.evaluate().value
        }
    }

    @Test
    fun `test custom function with QUERY`() {
        val evalExExpression = "$FUNC('QUERY=fields')"
        val expression = Expression(evalExExpression, configuration).with("QUERY", "fields").evaluate().value
        assertTrue(expression as Boolean)
    }

    @Test
    fun `test custom function with NOT QUERY`() {
        val evalExExpression = "$FUNC('QUERY!=fields')"
        val expression = Expression(evalExExpression, configuration).with("QUERY", "fields").evaluate().value
        assertFalse(expression as Boolean)
    }

    @Test
    fun `test custom function with multiple QUERY`() {
        val evalExExpression = "$FUNC('QUERY=fields,startId')"
        val expression = Expression(evalExExpression, configuration).with("QUERY", "fields").evaluate().value
        val expression1 = Expression(evalExExpression, configuration).with("QUERY", "startId").evaluate().value
        val expression2 = Expression(evalExExpression, configuration).with("QUERY", "hello").evaluate().value

        assertTrue(expression as Boolean)
        assertTrue(expression1 as Boolean)
        assertFalse(expression2 as Boolean)
    }

    @Test
    fun `test custom function with multiple NOT QUERY`() {
        val evalExExpression = "$FUNC('QUERY!=fields,startId')"
        val expression = Expression(evalExExpression, configuration).with("QUERY", "fields").evaluate().value
        val expression1 = Expression(evalExExpression, configuration).with("QUERY", "startId").evaluate().value
        val expression2 = Expression(evalExExpression, configuration).with("QUERY", "hello").evaluate().value

        assertFalse(expression as Boolean)
        assertFalse(expression1 as Boolean)
        assertTrue(expression2 as Boolean)
    }

    @Test
    fun `test custom function with HEADERS`() {
        val evalExExpression = "$FUNC('HEADERS=sortBy')"
        val expression = Expression(evalExExpression, configuration).with("HEADERS", "sortBy").evaluate().value
        assertTrue(expression as Boolean)
    }

    @Test
    fun `test custom function with NOT HEADERS`() {
        val evalExExpression = "$FUNC('HEADERS!=sortBy')"
        val expression = Expression(evalExExpression, configuration).with("HEADERS", "sortBy").evaluate().value
        assertFalse(expression as Boolean)
    }

    @Test
    fun `test custom function with multiple HEADERS`() {
        val evalExExpression = "$FUNC('HEADERS=sortBy,request-id')"
        val expression = Expression(evalExExpression, configuration).with("HEADERS", "sortBy").evaluate().value
        val expression1 = Expression(evalExExpression, configuration).with("HEADERS", "request-id").evaluate().value
        val expression2 = Expression(evalExExpression, configuration).with("HEADERS", "hello").evaluate().value

        assertTrue(expression as Boolean)
        assertTrue(expression1 as Boolean)
        assertFalse(expression2 as Boolean)
    }

    @Test
    fun `test custom function with multiple NOT HEADERS`() {
        val evalExExpression = "$FUNC('HEADERS!=sortBy,request-id')"
        val expression = Expression(evalExExpression, configuration).with("HEADERS", "sortBy").evaluate().value
        val expression1 = Expression(evalExExpression, configuration).with("HEADERS", "request-id").evaluate().value
        val expression2 = Expression(evalExExpression, configuration).with("HEADERS", "hello").evaluate().value

        assertFalse(expression as Boolean)
        assertFalse(expression1 as Boolean)
        assertTrue(expression2 as Boolean)
    }
}
