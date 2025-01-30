package io.specmatic.core.filters

import io.specmatic.core.filters.ScenarioMetadataFilter.Companion.ENHANCED_FUNC_NAME
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScenarioMetadataFilterTests {
    private fun createScenarioMetadata(
        method: String = "GET",
        path: String = "/default",
        statusCode: Int = 200,
        header: Set<String> = emptySet(),
        query: Set<String> = emptySet()
    ): ScenarioMetadata {
        return ScenarioMetadata(
            method = method,
            path = path,
            statusCode = statusCode,
            header = header,
            query = query,
            exampleName = "example"
        )
    }

    @Test
    fun `empty filter includes everything`() {
        val filter = ScenarioMetadataFilter.from("")

        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val putProducts = createScenarioMetadata(method = "PUT", path = "/products")
        val getUsers = createScenarioMetadata(method = "GET", path = "/users")
        val scenarios = listOf(
            getProducts,
            postProducts,
            putProducts,
            getUsers
        )

        scenarios.forEach { scenario ->
            assertTrue(filter.isSatisfiedBy(scenario))
        }
    }

    @Test
    fun `wrong filter syntaxes fails silently and includes everything`() {
        val filter = ScenarioMetadataFilter.from("PATH='/products' &| METHOD='GET,POST'")

        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val putProducts = createScenarioMetadata(method = "PUT", path = "/products")
        val getUsers = createScenarioMetadata(method = "GET", path = "/users")
        val scenarios = listOf(
            getProducts,
            postProducts,
            putProducts,
            getUsers
        )
        scenarios.forEach { scenario ->
            assertThrows<Exception> {
                filter.isSatisfiedBy(scenario)
            }
        }
    }

    @Test
    fun `filter by PATH and METHOD`() {
        val filter = ScenarioMetadataFilter.from("PATH='/products' && METHOD='GET,POST'")

        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val putProducts = createScenarioMetadata(method = "PUT", path = "/products")
        val getUsers = createScenarioMetadata(method = "GET", path = "/users")

        assertTrue(filter.isSatisfiedBy(getProducts))
        assertTrue(filter.isSatisfiedBy(postProducts))
        assertFalse(filter.isSatisfiedBy(putProducts))
        assertFalse(filter.isSatisfiedBy(getUsers))
    }

    @Test
    fun `filter by HEADER`() {
        val filter = ScenarioMetadataFilter.from("HEADERS='Content-Type'")
        val contentTypeHeader = createScenarioMetadata(header = setOf("Content-Type"))
        assertTrue(filter.isSatisfiedBy(contentTypeHeader))
    }

    @Test
    fun `filter by QUERY`() {
        val filter = ScenarioMetadataFilter.from("QUERY='fields'")
        val getQueryFields = createScenarioMetadata(query = setOf("fields"))
        assertTrue(filter.isSatisfiedBy(getQueryFields))
    }

    @Test
    fun `filter by Relative Path`() {
        val filter = ScenarioMetadataFilter.from("PATH='/products/*/1'")
        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(path = "/products/car/1")))
        assertTrue(filter.isSatisfiedBy( createScenarioMetadata(path = "/products/bike/1")))
        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(path = "/products/car/bike/1")))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(path = "/product/bike/2")))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(path = "/products/bike/2")))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(path = "/products/bike/1/2")))
    }

    @Test
    fun `filter by STATUS 200 or 400`() {
        val filter = ScenarioMetadataFilter.from("STATUS='200, 400'")

        val status200 = createScenarioMetadata(statusCode = 200)
        val status400 = createScenarioMetadata(statusCode = 400)
        val status500 = createScenarioMetadata(statusCode = 500)

        assertTrue(filter.isSatisfiedBy(status200))
        assertTrue(filter.isSatisfiedBy(status400))
        assertFalse(filter.isSatisfiedBy(status500))
    }

    @Test
    fun `filter by STATUS not 200 or 400`() {
        val filter = ScenarioMetadataFilter.from("STATUS!='200,400'")
        val status500 = createScenarioMetadata(statusCode = 500)
        val status200 = createScenarioMetadata(statusCode = 200)
        val status400 = createScenarioMetadata(statusCode = 400)

        assertTrue(filter.isSatisfiedBy(status500))
        assertFalse(filter.isSatisfiedBy(status200))
        assertFalse(filter.isSatisfiedBy(status400))
    }

    @Test
    fun `filter by STATUS 2xx`() {
        val filter = ScenarioMetadataFilter.from("STATUS='2xx'")

        val status200 = createScenarioMetadata(statusCode = 200)
        val status201 = createScenarioMetadata(statusCode = 201)
        val status500 = createScenarioMetadata(statusCode = 500)

        assertTrue(filter.isSatisfiedBy(status200))
        assertTrue(filter.isSatisfiedBy(status201))
        assertFalse(filter.isSatisfiedBy(status500))
    }

    @Test
    fun `filter by METHOD not GET and PATH not users`() {
        val filter = ScenarioMetadataFilter.from("METHOD!='GET' || PATH!='/users'")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postUsers = createScenarioMetadata(method = "POST", path = "/users")
        val getUsers = createScenarioMetadata(method = "GET", path = "/users")


        assertTrue(filter.isSatisfiedBy(postProducts))
        assertTrue(filter.isSatisfiedBy(getProducts))
        assertTrue(filter.isSatisfiedBy(postUsers))
        assertFalse(filter.isSatisfiedBy(getUsers))
    }

    @Test
    fun `complex filter with OR`() {
        val filter = ScenarioMetadataFilter.from("PATH='/products' || METHOD='POST'")
        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val postUsers = createScenarioMetadata(method = "POST", path = "/users")
        val getUsers = createScenarioMetadata(method = "PUT", path = "/users")

        assertTrue(filter.isSatisfiedBy(getProducts))
        assertTrue(filter.isSatisfiedBy(postProducts))
        assertTrue(filter.isSatisfiedBy(postUsers))
        assertFalse(filter.isSatisfiedBy(getUsers))
    }

    @Test
    fun `exclude scenarios with STATUS 202`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202")
        val status200 = createScenarioMetadata(statusCode = 200)
        val status202 = createScenarioMetadata(statusCode = 202)
        val status400 = createScenarioMetadata(statusCode = 400)

        assertTrue(filter.isSatisfiedBy(status200))
        assertFalse(filter.isSatisfiedBy(status202))
        assertTrue(filter.isSatisfiedBy(status400))
    }

    @Test
    fun `exclude scenarios by example name with exact match`() {
        val filter = ScenarioMetadataFilter.from("PATH!='/hub,/hub/(id:string)'")

        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(path = "/hub")))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(path = "/hub/(id:string)")))
        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(path = "/hub/id")))
        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(path = "/hub/string")))
        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(path = "/users")))
    }

    @Test
    fun `exclude scenarios with STATUS not in a list`() {
        val filter = ScenarioMetadataFilter.from("STATUS!='202,401,403'")
        val status200 = createScenarioMetadata(statusCode = 200)
        val status401 = createScenarioMetadata(statusCode = 401)
        val status202 = createScenarioMetadata(statusCode = 202)

        assertTrue(filter.isSatisfiedBy(status200))
        assertFalse(filter.isSatisfiedBy(status401))
        assertFalse(filter.isSatisfiedBy(status202))
    }


    @Test
    fun `exclude scenarios by list of status codes including range expression`() {
        val filter = ScenarioMetadataFilter.from("STATUS!='202,401,403,405,5xx'")
        val status202 = createScenarioMetadata(statusCode = 202)
        val status500 = createScenarioMetadata(statusCode = 500)
        val status201 = createScenarioMetadata(statusCode = 201)

        assertFalse(filter.isSatisfiedBy(status202))
        assertFalse(filter.isSatisfiedBy(status500))
        assertTrue(filter.isSatisfiedBy(status201))
    }

    @Test
    fun `exclude scenarios with combined STATUS and path conditions`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202 && PATH!='/hub,/hub/(id:string)'")
        val users200 = createScenarioMetadata(statusCode = 200, path = "/users")
        val users202 = createScenarioMetadata(statusCode = 202, path = "/users")
        val hub200 = createScenarioMetadata(statusCode = 200, path = "/hub")
        val hub202 = createScenarioMetadata(statusCode = 202, path = "/hub")
        val hubWithId202 = createScenarioMetadata(statusCode = 202, path = "/hub/(id:string)")

        assertTrue(filter.isSatisfiedBy(users200))
        assertFalse(filter.isSatisfiedBy(users202))
        assertFalse(filter.isSatisfiedBy(hub200))
        assertFalse(filter.isSatisfiedBy(hub202))
        assertFalse(filter.isSatisfiedBy(hubWithId202))
    }

    @Test
    fun `include scenarios with combined METHOD and PATH conditions`() {
        val filter = ScenarioMetadataFilter.from("(PATH='/users' && METHOD='POST') || (PATH='/products' && METHOD='POST')")
        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val getUsers = createScenarioMetadata(method = "GET", path = "/users")
        val postUsers = createScenarioMetadata(method = "POST", path = "/users")
        val postOrders = createScenarioMetadata(method = "POST", path = "/orders")


        assertFalse(filter.isSatisfiedBy(getProducts))
        assertTrue(filter.isSatisfiedBy(postProducts))
        assertFalse(filter.isSatisfiedBy(getUsers))
        assertTrue(filter.isSatisfiedBy(postUsers))
        assertFalse(filter.isSatisfiedBy(postOrders))
    }

    @Test
    fun `include scenarios where nothing matches`() {
        val filter = ScenarioMetadataFilter.from("(PATH='/users' && METHOD='POST') && (PATH='/products' && METHOD='POST')")
        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val getUsers = createScenarioMetadata(method = "GET", path = "/users")
        val postUsers = createScenarioMetadata(method = "POST", path = "/users")
        val postOrders = createScenarioMetadata(method = "POST", path = "/orders")


        assertFalse(filter.isSatisfiedBy(getProducts))
        assertFalse(filter.isSatisfiedBy(postProducts))
        assertFalse(filter.isSatisfiedBy(getUsers))
        assertFalse(filter.isSatisfiedBy(postUsers))
        assertFalse(filter.isSatisfiedBy(postOrders))
    }

    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions`() {
        val filter = ScenarioMetadataFilter.from("!(PATH='/users' && METHOD='POST') && !(PATH='/products' && METHOD='POST')")
        val getProducts = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val getUsers = createScenarioMetadata(method = "GET", path = "/users")
        val postUsers = createScenarioMetadata(method = "POST", path = "/users")
        val postOrders = createScenarioMetadata(method = "POST", path = "/orders")


        assertTrue(filter.isSatisfiedBy(getProducts))
        assertFalse(filter.isSatisfiedBy(postProducts))
        assertTrue(filter.isSatisfiedBy(getUsers))
        assertFalse(filter.isSatisfiedBy(postUsers))
        assertTrue(filter.isSatisfiedBy(postOrders))
    }

    @Test
    fun `double nested conditions`() {
        val filter = ScenarioMetadataFilter.from("!(STATUS='202,401,403,405' || STATUS='50x' || (PATH='/monitor' && METHOD='GET') || (PATH='/monitor/(id:string)' && METHOD='GET')) && (PATH='/orders' && METHOD='GET')")
        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/orders", statusCode = 200)))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/products", statusCode = 200)))
    }

    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions, in addition also a status condition`() {
        val filter = ScenarioMetadataFilter.from("!(PATH='/users' && METHOD='POST') && !(PATH='/products' && METHOD='POST') && STATUS!='202,400,500'")

        val getProducts200 = createScenarioMetadata(method = "GET", path = "/products", statusCode = 200)
        val getProducts202 = createScenarioMetadata(method = "GET", path = "/products", statusCode = 202)

        val postProducts200 = createScenarioMetadata(method = "POST", path = "/products", statusCode = 200)
        val postProducts202 = createScenarioMetadata(method = "POST", path = "/products", statusCode = 202)

        val getUsers200 = createScenarioMetadata(method = "GET", path = "/users", statusCode = 200)
        val getUsers202 = createScenarioMetadata(method = "GET", path = "/users", statusCode = 202)

        val postUsers401 = createScenarioMetadata(method = "POST", path = "/users", statusCode = 401)
        val postUsers400 = createScenarioMetadata(method = "POST", path = "/users", statusCode = 400)

        val postOrders401 = createScenarioMetadata(method = "POST", path = "/orders", statusCode = 401)
        val postOrders500 = createScenarioMetadata(method = "POST", path = "/orders", statusCode = 500)


        assertTrue(filter.isSatisfiedBy(getProducts200))
        assertFalse(filter.isSatisfiedBy(getProducts202))

        assertFalse(filter.isSatisfiedBy(postProducts200))
        assertFalse(filter.isSatisfiedBy(postProducts202))

        assertTrue(filter.isSatisfiedBy(getUsers200))
        assertFalse(filter.isSatisfiedBy(getUsers202))

        assertFalse(filter.isSatisfiedBy(postUsers401))
        assertFalse(filter.isSatisfiedBy(postUsers400))

        assertTrue(filter.isSatisfiedBy(postOrders401))
        assertFalse(filter.isSatisfiedBy(postOrders500))
    }

    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions, in addition also a status condition as first condition`() {
        val filter = ScenarioMetadataFilter.from("(STATUS!='202,400' || (!(PATH='/users' && METHOD='POST')) && !(PATH='/products' && METHOD='POST') && STATUS!='5xx')")

        val getProducts200 = createScenarioMetadata(method = "GET", path = "/products", statusCode = 200)
        val getProducts202 = createScenarioMetadata(method = "GET", path = "/products", statusCode = 202)

        val postProducts200 = createScenarioMetadata(method = "POST", path = "/products", statusCode = 200)
        val postProducts202 = createScenarioMetadata(method = "POST", path = "/products", statusCode = 202)

        val getUsers200 = createScenarioMetadata(method = "GET", path = "/users", statusCode = 200)
        val getUsers202 = createScenarioMetadata(method = "GET", path = "/users", statusCode = 202)

        val postUsers401 = createScenarioMetadata(method = "POST", path = "/users", statusCode = 401)
        val postUsers400 = createScenarioMetadata(method = "POST", path = "/users", statusCode = 400)

        val postOrders401 = createScenarioMetadata(method = "POST", path = "/orders", statusCode = 401)
        val postOrders500 = createScenarioMetadata(method = "POST", path = "/orders", statusCode = 500)
        val postOrders502 = createScenarioMetadata(method = "POST", path = "/orders", statusCode = 502)


        assertTrue(filter.isSatisfiedBy(getProducts200))
        assertTrue(filter.isSatisfiedBy(getProducts202))

        assertTrue(filter.isSatisfiedBy(postProducts200))
        assertFalse(filter.isSatisfiedBy(postProducts202))

        assertTrue(filter.isSatisfiedBy(getUsers200))
        assertTrue(filter.isSatisfiedBy(getUsers202))

        assertTrue(filter.isSatisfiedBy(postUsers401))
        assertFalse(filter.isSatisfiedBy(postUsers400))

        assertTrue(filter.isSatisfiedBy(postOrders401))
        assertTrue(filter.isSatisfiedBy(postOrders500))

        assertTrue(filter.isSatisfiedBy(postOrders502))
    }

    @Test
    fun `exclude scenarios with wildcard only for last digit in status codes`() {
        val filter = ScenarioMetadataFilter.from("STATUS!='50x'")

        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/products", statusCode = 521)))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/products", statusCode = 502)))
    }

    @Test
    fun `exclude scenarios with wildcard only for last digit including tildle`() {
        val filter = ScenarioMetadataFilter.from("STATUS~'50x'")

        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/products", statusCode = 521)))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/products", statusCode = 502)))
    }

    @Test
    fun `test enhanced expression with only METHOD expression`() {
        val filterExpression = "METHOD='GET'"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with METHOD and STATUS expression`() {
        val filterExpression = "METHOD='GET' && STATUS='200,400'"
        val expectedEnhancedExpression = "METHOD='GET' && $ENHANCED_FUNC_NAME('STATUS=200,400')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression,enhancedExpression)
    }

    @Test
    fun `test enhanced expression with multiple METHOD and STATUS expression`() {
        val filterExpression = "METHOD='GET,POST' && STATUS='200,400'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('METHOD=GET,POST') && $ENHANCED_FUNC_NAME('STATUS=200,400')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with multiple METHOD and PATH expression`() {
        val filterExpression = "METHOD='GET,POST' || PATH='/users,/user(id:string)'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('METHOD=GET,POST') || $ENHANCED_FUNC_NAME('PATH=/users,/user(id:string)')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with multiple METHOD and single PATH expression`() {
        val filterExpression = "(METHOD='POST' && PATH='/users') || (METHOD='POST' && PATH='/products')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with STATUS expression`() {
        val filterExpression = "STATUS='2xx'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('STATUS=2xx')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with PATH expression`() {
        val filterExpression = "STATUS!=202 && PATH!='/hub,/hub/(id:string)'"
        val expectedEnhancedExpression = "STATUS!=202 && $ENHANCED_FUNC_NAME('PATH!=/hub,/hub/(id:string)')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with QUERY expression`() {
        val filterExpression = "QUERY='fields'"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with empty expression`() {
        val filterExpression = ""
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with multiple QUERY expressions`() {
        val filterExpression = "QUERY='name,age' && QUERY='location'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('QUERY=name,age') && QUERY='location'"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with multiple HEADER expressions`() {
        val filterExpression = "HEADER='Content-Type,Accept' && HEADER='Authorization'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('HEADER=Content-Type,Accept') && HEADER='Authorization'"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with mixed operators`() {
        val filterExpression = "METHOD='GET,POST' && STATUS!='200,400'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('METHOD=GET,POST') && $ENHANCED_FUNC_NAME('STATUS!=200,400')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with no $ENHANCED_FUNCTION_NAME applicable`() {
        val filterExpression = "METHOD='GET' && STATUS='200'"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with special characters`() {
        val filterExpression = "PATH='/user(id:string),/user(name:string)'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('PATH=/user(id:string),/user(name:string)')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression does not handle spaces around =`() {
        val filterExpression = "METHOD = 'GET, POST' && STATUS = '200, 400'"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression handles spaces`() {
        val filterExpression = "METHOD='GET, POST' && STATUS='200, 400'"
        val expectedEnhancedExpression = "$ENHANCED_FUNC_NAME('METHOD=GET, POST') && $ENHANCED_FUNC_NAME('STATUS=200, 400')"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(expectedEnhancedExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with empty $ENHANCED_FUNCTION_NAME`() {
        val filterExpression = "METHOD='' && STATUS=''"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }

    @Test
    fun `test enhanced expression with single quotes inside`() {
        val filterExpression = "METHOD='GET' && STATUS='2'00'"
        val enhancedExpression = ScenarioMetadataFilter.standardizeExpression(filterExpression)
        assertEquals(filterExpression, enhancedExpression)
    }
}
