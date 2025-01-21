//package io.specmatic.core.filters
//
//import com.ezylang.evalex.Expression
//import com.ezylang.evalex.config.ExpressionConfiguration
//import com.ezylang.evalex.data.EvaluationValue
//import com.ezylang.evalex.functions.FunctionIfc
//import org.junit.jupiter.api.Assertions.*
//import org.junit.jupiter.api.Test
//
//class CustomExpressionTest {
//
//
//    @Test
//    fun `test standardizeExpression with basic expressions`() {
//        val exp = "METHOD=GET && STATUS=200,400"
//        val expected1 = "(METHOD==\"GET\" && CSV(\"STATUS=200,400\"))"
////        val expected2 = "(METHOD==\"GET\" && (STATUS==500 || STATUS==501)) || (METHOD==\"POST\" &&  (STATUS==500 || STATUS==501))"
//        val configuration = ExpressionConfiguration.defaultConfiguration()
//            .withAdditionalFunctions(
//                mapOf(Pair("CSV", CSVFunction())).entries.single()
//            )
//

//        val expressionStr1 = "METHOD==\"GET\" && CSV(\"STATUS\", \"==\" ,201, 202, 203)"
//        val expressionStr2 = "CSV('METHOD', 'GET', 'POST')"
//        val expressionStr3 = "CSV('PATH', '/users', '/users/v1')"
//        val expression = Expression(expected1, configuration).with("METHOD", "GET").with("STATUS", 405)
//        val result = expression.evaluate().value
//
//        assertTrue(result as Boolean)
//    }
//}
//    @Test
//    fun `test standardizeExpression with method and path expressions`() {
//        val expression = "METHOD=GET && PATH=/monitor,/monitor/v1"
//        val expected = "METHOD==\"GET\" && (PATH==\"/monitor\" || PATH==\"/monitor/v1\")"
//
//        val result = converter.standardizeExpression(expression)
//
//        val evalExpGet = Expression(result).with("METHOD","GET").with("PATH","/monitor").evaluate()
//        val value1 = evalExpGet.value
//
//        val evalExpPost = Expression(result).with("METHOD","POST").with("PATH","/monitor").evaluate()
//        val value2 = evalExpPost.value
//
//        assertEquals(expected, result)
//        assertTrue(value1 as Boolean)
//        assertFalse(value2 as Boolean)
//    }
//
//    @Test
//    fun `test standardizeExpression with multiple method expression`() {
//        val expression = "METHOD=GET,POST && PATH=/monitor,/monitor/v1"
//        val expected = "(METHOD==\"GET\" || METHOD==\"POST\") && (PATH==\"/monitor\" || PATH==\"/monitor/v1\")"
//        val result = converter.standardizeExpression(expression)
//        assertEquals(expected, result)
//    }
//
//    @Test
//    fun `test standardizeExpression with method and path id expression`() {
//        val expression = "METHOD=GET && PATH=/monitor,/monitor/(id:string)"
//        val expected = "METHOD==\"GET\" && (PATH==\"/monitor\" || PATH==\"/monitor/(id:string)\")"
//        val result = converter.standardizeExpression(expression)
//        assertEquals(expected, result)
//    }
//
//    @Test
//    fun `test standardizeExpression with multiple status values`() {
//        val expression = "STATUS=200,201,202"
//        val expected = "(STATUS==200 || STATUS==201 || STATUS==202)"
//        val result = converter.standardizeExpression(expression)
//        assertEquals(expected, result)
//    }
//
//    @Test
//    fun `test standardizeExpression with multiple filter not status values`() {
//        val expression = "STATUS!=200,201,202"
//        val expected = "(STATUS!=200 && STATUS!=201 && STATUS!=202)"
//        val result = converter.standardizeExpression(expression)
//        assertEquals(expected, result)
//    }
//
//
//    @Test
//    fun `test standardizeExpression with x status range`() {
//        val expression = "STATUS=20x"
//        val expected = "(STATUS==200 || STATUS==201 || STATUS==202 || STATUS==203 || STATUS==204 || STATUS==205 || STATUS==206 || STATUS==207 || STATUS==208 || STATUS==209)"
//        val result = converter.standardizeExpression(expression)
//        assertEquals(expected, result)
//    }
//
//    @Test
//    fun `test standardizeExpression with xx status range`() {
//        val expression = "STATUS=5xx"
//        val expected = (500..599).joinToString(" || ") { "STATUS==$it" }.let { "($it)" }
//        val result = converter.standardizeExpression(expression)
//        assertEquals(expected, result)
//    }
//
//    @Test
//    fun `test standardizeExpression with xx and x status range`() {
//        val expression = "STATUS=5xx,20x"
//        val expectedRange = (500..599).joinToString(" || ") { "STATUS==$it" } + " || " +
//                (200..209).joinToString(" || ") { "STATUS==$it" }
//        val expected = "($expectedRange)"
//        val result = converter.standardizeExpression(expression)
//        assertEquals(expected, result)
//    }
//}
