package io.specmatic.core.filters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CSVFunctionExpressionModifierTest {

    private val converter = CSVFunctionExpressionModifier()

    @Test
    fun `test standard expression with only METHOD expression`() {
        val expression = "METHOD='GET'"
        val expected = "METHOD='GET'"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with METHOD and STATUS expression`() {
        val expression = "METHOD='GET' && STATUS='200,400'"
        val expected = "METHOD='GET' && CSV('STATUS=200,400')"
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and STATUS expression`() {
        val expression = "METHOD='GET,POST' && STATUS='200,400'"
        val expected = "CSV('METHOD=GET,POST') && CSV('STATUS=200,400')"
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and PATH expression`() {
        val expression = "METHOD='GET,POST' || PATH='/users,/user(id:string)'"
        val expected = "CSV('METHOD=GET,POST') || CSV('PATH=/users,/user(id:string)')"
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and single PATH expression`() {
        val expression = "(METHOD='POST' && PATH='/users') || (METHOD='POST' && PATH='/products')"
        val expected = "(METHOD='POST' && PATH='/users') || (METHOD='POST' && PATH='/products')"
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with STATUS expression`() {
        val expression = "STATUS='2xx'"
        val expected = "CSV('STATUS=2xx')"
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with PATH expression`() {
        val expression = "STATUS!=202 && PATH!='/hub,/hub/(id:string)'"
        val expected = "STATUS!=202 && CSV('PATH!=/hub,/hub/(id:string)')"
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with QUERY expression`() {
        val expression = "QUERY='fields'"
        val expected = "QUERY='fields'"
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with empty expression`() {
        val expression = ""
        val expected = ""
        val standardExpression= converter.standardizeExpression(expression)
        assertEquals(expected,standardExpression)
    }

    @Test
    fun `test standard expression with multiple QUERY expressions`() {
        val expression = "QUERY='name,age' && QUERY='location'"
        val expected = "CSV('QUERY=name,age') && QUERY='location'"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with multiple HEADER expressions`() {
        val expression = "HEADER='Content-Type,Accept' && HEADER='Authorization'"
        val expected = "CSV('HEADER=Content-Type,Accept') && HEADER='Authorization'"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with mixed operators`() {
        val expression = "METHOD='GET,POST' && STATUS!='200,400'"
        val expected = "CSV('METHOD=GET,POST') && CSV('STATUS!=200,400')"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with no CSV applicable`() {
        val expression = "METHOD='GET' && STATUS='200'"
        val expected = "METHOD='GET' && STATUS='200'"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with special characters`() {
        val expression = "PATH='/user(id:string),/user(name:string)'"
        val expected = "CSV('PATH=/user(id:string),/user(name:string)')"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression does not handle spaces around =`() {
        val expression = "METHOD = 'GET, POST' && STATUS = '200, 400'"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expression, standardExpression)
    }

    @Test
    fun `test standard expression handles spaces`() {
        val expression = "METHOD='GET, POST' && STATUS='200, 400'"
        val expected = "CSV(METHOD='GET, POST') && CSV(STATUS='200, 400')"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with empty CSV`() {
        val expression = "METHOD='' && STATUS=''"
        val expected = "METHOD='' && STATUS=''"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with single quotes inside`() {
        val expression = "METHOD='GET' && STATUS='2'00'"
        val expected = "METHOD='GET' && STATUS='2'00'"
        val standardExpression = converter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }
}