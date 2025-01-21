package io.specmatic.core.filters
import com.ezylang.evalex.Expression
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class EvalExTest {

    @Test
    fun `filter by PATH and METHOD Old Test`() {
        val oldExpression = "PATH=/products && METHOD=GET,POST"

        assertTrue(Expression(oldExpression).with("PATH","/products").with("METHOD","GET").evaluate().value as Boolean)
        assertTrue(Expression(oldExpression).with("PATH","/products").with("METHOD","POST").evaluate().value as Boolean)

        assertFalse(Expression(oldExpression).with("PATH","/products").with("METHOD","PATCH").evaluate().value as Boolean)
    }

    @Test
    fun `filter by PATH and METHOD`() {
        // Old Expression : "PATH=/products && METHOD=GET,POST"
        val evalExExpression = "PATH=\"/products\" && (METHOD=\"GET\" || METHOD=\"POST\")"

       assertTrue(Expression(evalExExpression).with("PATH","/products").with("METHOD","GET").evaluate().value as Boolean)
       assertTrue(Expression(evalExExpression).with("PATH","/products").with("METHOD","POST").evaluate().value as Boolean)

       assertFalse(Expression(evalExExpression).with("PATH","/products").with("METHOD","PATCH").evaluate().value as Boolean)
    }

    @Test
    fun `filter by HEADER`() {
        //Old Expression : HEADERS=Content-Type
        val evalExExpression = "HEADERS=\"Content-Type\""
        assertTrue(Expression(evalExExpression).with("HEADERS","Content-Type").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("HEADERS","application/json").evaluate().value as Boolean)

        org.junit.jupiter.api.assertThrows<Exception>(){
            //Variable name is HEADER instead of HEADERS
            Expression(evalExExpression).with("HEADER","Content-Type").evaluate().value as Boolean
        }
    }

    @Test
    fun `filter empty`() {
        //Old Expression : ""
        val evalExExpression = ""
        org.junit.jupiter.api.assertThrows<Exception>() {
            Expression(evalExExpression).evaluate().value as Boolean
        }

    }

    @Test
    fun `filter by QUERY`() {
        //Old Expression : QUERY=fields
        val evalExExpression = "QUERY=\"fields\""
        assertTrue(Expression(evalExExpression).with("QUERY","fields").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("QUERY","field").evaluate().value as Boolean)
    }

    @Test
    fun `filter by Relative Path`() {
        //Old Expression : PATH=/products/*/1
        val evalExExpression = "PATH=\"/products/*/1\""
        assertTrue(Expression(evalExExpression).with("PATH", "/products/car/1").evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("PATH", "/products/bike/1").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("PATH", "/products/plane/2").evaluate().value as Boolean)
    }

    @Test
    fun `filter by STATUS 200 or 400 Old Test`() {
        //Old Expression : STATUS=200,400
        val oldExpression = "STATUS=200,400"
        assertTrue(Expression(oldExpression).with("STATUS", 200).evaluate().value as Boolean)
        assertTrue(Expression(oldExpression).with("STATUS", 400).evaluate().value as Boolean)
        assertFalse(Expression(oldExpression).with("STATUS", 500).evaluate().value as Boolean)
    }

    @Test
    fun `filter by STATUS 200 or 400`() {
        //Old Expression : STATUS=200,400
        val evalExExpression = "STATUS=200 || STATUS=400"
        assertTrue(Expression(evalExExpression).with("STATUS", 200).evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("STATUS", 400).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 500).evaluate().value as Boolean)
    }

    @Test
    fun `filter by STATUS 2xx`() {
        val evalExExpression = "STATUS=2xx"
        Expression(evalExExpression).with("STATUS", 200).evaluate().value as Boolean
        Expression(evalExExpression).with("STATUS", 201).evaluate().value as Boolean
        Expression(evalExExpression).with("STATUS", 500).evaluate().value as Boolean
    }


    @Test
    fun `filter by METHOD not GET and PATH not users`() {
        //Old Expression : METHOD!=GET && PATH!=/users
        val evalExExpression = "METHOD!=\"GET\" && PATH!=\"/users\""
        assertFalse(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/users").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/users").evaluate().value as Boolean)

        assertTrue(Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/products").evaluate().value as Boolean)
    }

    @Test
    fun `filter by STATUS not 200 or 400`() {
        //Old Expression : STATUS!=200,400
        val evalExExpression = "STATUS!=200 && STATUS!=400"
        assertTrue(Expression(evalExExpression).with("STATUS", 500).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 200).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 400).evaluate().value as Boolean)
    }
    @Test
    fun `complex filter with OR`() {
        //Old Expression : PATH=/products || METHOD=POST
        val evalExExpression = "PATH=\"/products\" || METHOD=\"POST\""
        assertTrue(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/users").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("METHOD", "PUT").with("PATH", "/users").evaluate().value as Boolean)
    }
    @Test
    fun `exclude scenarios with STATUS 202`() {
        val evalExExpression = "STATUS!=202"
        assertTrue(Expression(evalExExpression).with("STATUS", 200).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 202).evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("STATUS", 400).evaluate().value as Boolean)
    }

    @Test
    fun `exclude scenarios by example name with exact match`() {
        // Old Expression : PATH!=/hub,/hub/(id:string)
        val evalExExpression = "PATH!=\"/hub\" && PATH!=\"/hub/(id:string)\""
        assertFalse(Expression(evalExExpression).with("PATH", "/hub").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("PATH", "/hub/(id:string)").evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("PATH", "/users").evaluate().value as Boolean)
    }

    @Test
    fun `exclude scenarios by list of status codes`() {
        // Old Expression :  STATUS!=202,401,403,405 && STATUS!=5xx
        val evalExExpression = "STATUS!=202 && STATUS!=401 && STATUS!=403 && STATUS!=405 && STATUS!=5xx"
        assertFalse(Expression(evalExExpression).with("STATUS", 202).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 500).evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("STATUS", 201).evaluate().value as Boolean)
    }

    @Test
    fun `exclude scenarios by list of status codes without wildcard`() {
        // Old Expression :  STATUS!=202,401,403,405
        val evalExExpression = "STATUS!=202 && STATUS!=401 && STATUS!=403 && STATUS!=405"
        assertFalse(Expression(evalExExpression).with("STATUS", 202).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 405).evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("STATUS", 201).evaluate().value as Boolean)
    }
//
    @Test
    fun `exclude scenarios with STATUS not in a list`() {
        //Old Expression : STATUS!=202,401,403
        val evalExExpression = "STATUS!=202 && STATUS!=401 && STATUS!=403"
        assertTrue(Expression(evalExExpression).with("STATUS", 200).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 401).evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 202).evaluate().value as Boolean)
    }
//
    @Test
    fun `exclude scenarios with combined STATUS and path conditions`() {
        //Old Expression : STATUS!=202 && PATH!=/hub,/hub/(id:string)
        val evalExExpression = "STATUS!=202 && PATH!=\"/hub\" && PATH!=\"/hub/(id:string)\""
        assertTrue(Expression(evalExExpression).with("STATUS", 200).with("PATH", "/users").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 202).with("PATH", "/users").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 200).with("PATH", "/hub").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("STATUS", 202).with("PATH", "/hub").evaluate().value as Boolean)
    }
//
    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions`() {
        //Old Expression : !(PATH=/users && METHOD=POST) && !(PATH=/products && METHOD=POST)
        val evalExExpression = "!(PATH=\"/users\" && METHOD=\"POST\") && !(PATH=\"/products\" &&METHOD=\"POST\")"
        assertTrue(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/products").evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/users").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/users").evaluate().value as Boolean)
        assertTrue(Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/orders").evaluate().value as Boolean)
    }


    @Test
    fun `double nested conditions`() {
        //Old Expression : !(STATUS=202,401,403,405 || STATUS=50x || (PATH=/monitor && METHOD=GET) || (PATH=/monitor/(id:string) && METHOD=GET)) && (PATH=/orders && METHOD=GET)
        val evalExExpression = "!(STATUS=202 || STATUS=401 || STATUS=403 || STATUS=405 || STATUS=50x || (PATH=\"/monitor\" && METHOD=\"GET\") || (PATH=\"/monitor/(id:string)\" && METHOD=\"GET\")) && (PATH=\"/orders\" && METHOD=\"GET\")"
        assertTrue(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/orders").with("STATUS", "200").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", "200").evaluate().value as Boolean)
    }

    @Test
    fun `double nested conditions without wildcard`() {
        //Old Expression : !(STATUS=202,401,403,405 || (PATH=/monitor && METHOD=GET) || (PATH=/monitor/(id:string) && METHOD=GET)) && (PATH=/orders && METHOD=GET)
        val evalExExpression = "!(STATUS=202 || STATUS=401 || STATUS=403 || STATUS=405 || STATUS=500 || (PATH=\"/monitor\" && METHOD=\"GET\") || (PATH=\"/monitor/(id:string)\" && METHOD=\"GET\")) && (PATH=\"/orders\" && METHOD=\"GET\")"
        assertTrue(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/orders").with("STATUS", "200").evaluate().value as Boolean)
        assertFalse(Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", "200").evaluate().value as Boolean)
    }
    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions, in addition also a status condition`() {
        //Old Expression : !(PATH=/users && METHOD=POST) && !(PATH=/products && METHOD=POST) && STATUS!=202,400,500
        val evalExExpression = "!(PATH=\"/users\" && METHOD=\"POST\") && !(PATH=\"/products\" && METHOD=\"POST\") && STATUS!=202 && STATUS!=400 && STATUS!=500"

        val getProducts200 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", 200).evaluate().value as Boolean
        val getProducts202 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", 202).evaluate().value as Boolean

        val postProducts200 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/products").with("STATUS", 200).evaluate().value as Boolean
        val postProducts202 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/products").with("STATUS", 202).evaluate().value as Boolean

        val getUsers200 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/users").with("STATUS", 200).evaluate().value as Boolean
        val getUsers202 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/users").with("STATUS", 202).evaluate().value as Boolean

        val postUsers401 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/users").with("STATUS", 401).evaluate().value as Boolean
        val postUsers400 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/users").with("STATUS", 400).evaluate().value as Boolean

        val postOrders401 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/orders").with("STATUS", 401).evaluate().value as Boolean
        val postOrders500 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/orders").with("STATUS", 500).evaluate().value as Boolean

        assertTrue(getProducts200)
        assertFalse(getProducts202)

        assertFalse(postProducts200)
        assertFalse(postProducts202)

        assertTrue(getUsers200)
        assertFalse(getUsers202)

        assertFalse(postUsers401)
        assertFalse(postUsers400)

        assertTrue(postOrders401)
        assertFalse(postOrders500)
    }
//
    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions, in addition also a status condition as first condition`() {
        //Old Expression : STATUS!=202,400 && !(PATH=/users && METHOD=POST) && !(PATH=/products && METHOD=POST) && STATUS!=5xx
        val evalExExpression = "STATUS!=202 && STATUS!=400 && !(PATH=\"/users\" && METHOD=\"POST\") && !(PATH=\"/products\" && METHOD=\"POST\") && STATUS!=5xx"

        val getProducts200 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", 200).evaluate().value as Boolean
        val getProducts202 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", 202).evaluate().value as Boolean

        val postProducts200 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/products").with("STATUS", 200).evaluate().value as Boolean
        val postProducts202 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/products").with("STATUS", 200).evaluate().value as Boolean

        val getUsers200 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/users").with("STATUS", 200).evaluate().value as Boolean
        val getUsers202 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/users").with("STATUS", 200).evaluate().value as Boolean

        val postUsers401 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/users").with("STATUS", 401).evaluate().value as Boolean
        val postUsers400 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/users").with("STATUS", 400).evaluate().value as Boolean

        val postOrders401 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/orders").with("STATUS", 401).evaluate().value as Boolean
        val postOrders500 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/orders").with("STATUS", 500).evaluate().value as Boolean
        val postOrders502 = Expression(evalExExpression).with("METHOD", "POST").with("PATH", "/orders").with("STATUS", 502).evaluate().value as Boolean

        assertTrue(getProducts200)
        assertFalse(getProducts202)

        assertFalse(postProducts200)
        assertFalse(postProducts202)

        assertTrue(getUsers200)
        assertFalse(getUsers202)

        assertFalse(postUsers401)
        assertFalse(postUsers400)

        assertTrue(postOrders401)
        assertFalse(postOrders500)

        assertFalse(postOrders502)
}
    @Test
    fun `exclude scenarios with wildcard only for last digit in status codes`() {
        val evalExExpression = "STATUS!=50x"

        val getProducts521 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", 521).evaluate().value as Boolean
        val getProducts502 = Expression(evalExExpression).with("METHOD", "GET").with("PATH", "/products").with("STATUS", 502).evaluate().value as Boolean

        assertTrue(getProducts521)
        assertFalse(getProducts502)
    }

}
