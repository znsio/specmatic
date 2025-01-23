package io.specmatic.core.filters

import com.ezylang.evalex.config.ExpressionConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EvalExSyntaxConverterTest {

    private val evalExSyntaxConverter = EvalExSyntaxConverter()

    @Test
    fun `test standard expression with only METHOD expression`() {
        val expression = "METHOD='GET'"
        val expected = "METHOD='GET'"
        val standardExpression = evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with METHOD and STATUS expression`() {
        val expression = "METHOD='GET' && STATUS='200,400'"
        val expected = "METHOD='GET' && CSV('STATUS=200,400')"
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and STATUS expression`() {
        val expression = "METHOD='GET,POST' && STATUS='200,400'"
        val expected = "CSV('METHOD=GET,POST') && CSV('STATUS=200,400')"
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and PATH expression`() {
        val expression = "METHOD='GET,POST' || PATH='/users,/user(id:string)'"
        val expected = "CSV('METHOD=GET,POST') || CSV('PATH=/users,/user(id:string)')"
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and single PATH expression`() {
        val expression = "(METHOD='POST' && PATH='/users') || (METHOD='POST' && PATH='/products')"
        val expected = "(METHOD='POST' && PATH='/users') || (METHOD='POST' && PATH='/products')"
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with STATUS expression`() {
        val expression = "STATUS='2xx'"
        val expected = "CSV('STATUS=2xx')"
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with PATH expression`() {
        val expression = "STATUS!=202 && PATH!='/hub,/hub/(id:string)'"
        val expected = "STATUS!=202 && CSV('PATH!=/hub,/hub/(id:string)')"
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with QUERY expression`() {
        val expression = "QUERY='fields'"
        val expected = "QUERY='fields'"
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with empty expression`() {
        val expression = ""
        val expected = ""
        val standardExpression= evalExSyntaxConverter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }
}