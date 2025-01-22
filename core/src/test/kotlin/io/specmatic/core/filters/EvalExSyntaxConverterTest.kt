package io.specmatic.core.filters

import com.ezylang.evalex.config.ExpressionConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EvalExSyntaxConverterTest {

    val evalExSyntaxConverter = EvalExSyntaxConverter()

    @Test
    fun `test standardizeExpression with only METHOD expression`() {
        val expression = "METHOD=GET"
        val expected = "METHOD=\"GET\""
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with METHOD and STATUS expression`() {
        val expression = "METHOD=GET && STATUS=200,400"
        val expected = "METHOD=\"GET\" && CSV(\"STATUS=200,400\")"
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with multiple METHOD and STATUS expressions`() {
        val expression = "METHOD=GET,POST && STATUS=200,400"
        val expected = "CSV(\"METHOD=GET,POST\") && CSV(\"STATUS=200,400\")"
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with multiple METHOD and PATH expressions`() {
        val expression = "METHOD=GET,POST || PATH=/users,/user(id:string)"
        val expected = "CSV(\"METHOD=GET,POST\") || CSV(\"PATH=/users,/user(id:string)\")"
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with multiple METHOD and single PATH expressions`() {
        val expression = "(METHOD=POST && PATH=/users) || (METHOD=POST && PATH=/products)"
        val expected = "( METHOD=\"POST\" && PATH=\"/users\" ) || ( METHOD=\"POST\" && PATH=\"/products\" )"
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with STATUS expression`() {
        val expression = "STATUS=2xx"
        val expected = "CSV(\"STATUS=2xx\")"
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with PATH expression`() {
        val expression = "STATUS!=202 && PATH!=/hub,/hub/(id:string)"
        val expected = "STATUS!=202 && CSV(\"PATH!=/hub,/hub/(id:string)\")"
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with QUERY expression`() {
        val expression = "QUERY=fields"
        val expected = "QUERY=\"fields\""
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }

    @Test
    fun `test standardizeExpression with empty expression`() {
        val expression = ""
        val expected = ""
        val standardExpressions= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpressions)
    }
}