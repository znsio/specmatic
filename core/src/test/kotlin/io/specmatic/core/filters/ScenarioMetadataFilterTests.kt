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
        val metadata1 = createScenarioMetadata(query = setOf("fields"))
        assertTrue(filter.isSatisfiedBy(metadata1))
    }

    @Test
    fun `filter by Relative Path`() {
        val filter = ScenarioMetadataFilter.from("PATH='/products/*/1'")
        val metadata1 = createScenarioMetadata(path = "/products/car/1")
        val metadata2 = createScenarioMetadata(path = "/products/bike/1")
        val metadata3 = createScenarioMetadata(path = "/products/car/bike/1")
        val metadata4 = createScenarioMetadata(path = "/product/bike/2")
        val metadata5 = createScenarioMetadata(path = "/products/bike/2")
        val metadata6 = createScenarioMetadata(path = "/products/bike/1/2")
        assertTrue(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
        assertFalse(filter.isSatisfiedBy(metadata4))
        assertFalse(filter.isSatisfiedBy(metadata5))
        assertFalse(filter.isSatisfiedBy(metadata6))
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
        val metadata1 = createScenarioMetadata(statusCode = 500)
        val metadata2 = createScenarioMetadata(statusCode = 200)
        val metadata3 = createScenarioMetadata(statusCode = 400)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `filter by STATUS 2xx`() {
        val filter = ScenarioMetadataFilter.from("STATUS='2xx'")

        val metadata1 = createScenarioMetadata(statusCode = 200)
        val metadata2 = createScenarioMetadata(statusCode = 201)
        val metadata3 = createScenarioMetadata(statusCode = 500)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `filter by METHOD not GET and PATH not users`() {
        val filter = ScenarioMetadataFilter.from("METHOD!='GET' || PATH!='/users'")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val metadata2 = createScenarioMetadata(method = "GET", path = "/products")
        val metadata3 = createScenarioMetadata(method = "POST", path = "/users")
        val metadata4 = createScenarioMetadata(method = "GET", path = "/users")


        assertTrue(filter.isSatisfiedBy(postProducts))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
        assertFalse(filter.isSatisfiedBy(metadata4))
    }

    @Test
    fun `complex filter with OR`() {
        val filter = ScenarioMetadataFilter.from("PATH='/products' || METHOD='POST'")
        val metadata1 = createScenarioMetadata(method = "GET", path = "/products")
        val postProducts = createScenarioMetadata(method = "POST", path = "/products")
        val metadata2 = createScenarioMetadata(method = "POST", path = "/users")
        val metadata3 = createScenarioMetadata(method = "PUT", path = "/users")

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(postProducts))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios with STATUS 202`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202")
        val metadata1 = createScenarioMetadata(statusCode = 200)
        val metadata2 = createScenarioMetadata(statusCode = 202)
        val metadata3 = createScenarioMetadata(statusCode = 400)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios by example name with exact match`() {
        val filter = ScenarioMetadataFilter.from("PATH!='/hub,/hub/(id:string)'")
        val metadata1 = createScenarioMetadata(path = "/hub")
        val metadata2 = createScenarioMetadata(path = "/hub/(id:string)")
        val metadata3 = createScenarioMetadata(path = "/hub/id")
        val metadata4 = createScenarioMetadata(path = "/hub/string")
        val metadata5 = createScenarioMetadata(path = "/users")

        assertFalse(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
        assertTrue(filter.isSatisfiedBy(metadata4))
        assertTrue(filter.isSatisfiedBy(metadata5))
    }

    @Test
    fun `exclude scenarios with STATUS not in a list`() {
        val filter = ScenarioMetadataFilter.from("STATUS!='202,401,403'")
        val metadata1 = createScenarioMetadata(statusCode = 200)
        val metadata2 = createScenarioMetadata(statusCode = 401)
        val metadata3 = createScenarioMetadata(statusCode = 202)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }


    @Test
    fun `exclude scenarios by list of status codes including range expression`() {
        val filter = ScenarioMetadataFilter.from("STATUS!='202,401,403,405,5xx'")
        val metadata1 = createScenarioMetadata(statusCode = 202)
        val metadata2 = createScenarioMetadata(statusCode = 500)
        val metadata3 = createScenarioMetadata(statusCode = 201)

        assertFalse(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios with combined STATUS and path conditions`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202 && PATH!='/hub,/hub/(id:string)'")
        val metadata1 = createScenarioMetadata(statusCode = 200, path = "/users")
        val metadata2 = createScenarioMetadata(statusCode = 202, path = "/users")
        val metadata3 = createScenarioMetadata(statusCode = 200, path = "/hub")
        val metadata4 = createScenarioMetadata(statusCode = 202, path = "/hub")
        val metadata5 = createScenarioMetadata(statusCode = 202, path = "/hub/(id:string)")

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
        assertFalse(filter.isSatisfiedBy(metadata4))
        assertFalse(filter.isSatisfiedBy(metadata5))
    }

    @Test
    fun `include scenarios with combined METHOD and PATH conditions`() {
        val filter = ScenarioMetadataFilter.from("(PATH='/users' && METHOD='POST') || (PATH='/products' && METHOD='POST')")
        val metadata1 = createScenarioMetadata(method = "GET", path = "/products")
        val metadata2 = createScenarioMetadata(method = "POST", path = "/products")
        val metadata3 = createScenarioMetadata(method = "GET", path = "/users")
        val metadata4 = createScenarioMetadata(method = "POST", path = "/users")
        val metadata5 = createScenarioMetadata(method = "POST", path = "/orders")


        assertFalse(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
        assertTrue(filter.isSatisfiedBy(metadata4))
        assertFalse(filter.isSatisfiedBy(metadata5))
    }

    @Test
    fun `include scenarios where nothing matches`() {
        val filter = ScenarioMetadataFilter.from("(PATH='/users' && METHOD='POST') && (PATH='/products' && METHOD='POST')")
        val metadata1 = createScenarioMetadata(method = "GET", path = "/products")
        val metadata2 = createScenarioMetadata(method = "POST", path = "/products")
        val metadata3 = createScenarioMetadata(method = "GET", path = "/users")
        val metadata4 = createScenarioMetadata(method = "POST", path = "/users")
        val metadata5 = createScenarioMetadata(method = "POST", path = "/orders")


        assertFalse(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
        assertFalse(filter.isSatisfiedBy(metadata4))
        assertFalse(filter.isSatisfiedBy(metadata5))
    }

    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions`() {
        val filter = ScenarioMetadataFilter.from("!(PATH='/users' && METHOD='POST') && !(PATH='/products' && METHOD='POST')")
        val metadata1 = createScenarioMetadata(method = "GET", path = "/products")
        val metadata2 = createScenarioMetadata(method = "POST", path = "/products")
        val metadata3 = createScenarioMetadata(method = "GET", path = "/users")
        val metadata4 = createScenarioMetadata(method = "POST", path = "/users")
        val metadata5 = createScenarioMetadata(method = "POST", path = "/orders")


        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
        assertFalse(filter.isSatisfiedBy(metadata4))
        assertTrue(filter.isSatisfiedBy(metadata5))
    }

    @Test
    fun `double nested conditions`() {
        val filter =
            ScenarioMetadataFilter.from("!(STATUS='202,401,403,405' || STATUS='50x' || (PATH='/monitor' && METHOD='GET') || (PATH='/monitor/(id:string)' && METHOD='GET')) && (PATH='/orders' && METHOD='GET')")
        assertTrue(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/orders", statusCode = 200)))
        assertFalse(filter.isSatisfiedBy(createScenarioMetadata(method = "GET", path = "/products", statusCode = 200)))
    }

    @Test
    fun `exclude scenarios with combined METHOD and PATH conditions, in addition also a status condition`() {
        val filter =
            ScenarioMetadataFilter.from("!(PATH='/users' && METHOD='POST') && !(PATH='/products' && METHOD='POST') && STATUS!='202,400,500'")

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
        val filter =
            ScenarioMetadataFilter.from("(STATUS!='202,400' || (!(PATH='/users' && METHOD='POST')) && !(PATH='/products' && METHOD='POST') && STATUS!='5xx')")

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
    fun `test standard expression with only METHOD expression`() {
        val expression = "METHOD='GET'"
        val expected = "METHOD='GET'"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with METHOD and STATUS expression`() {
        val expression = "METHOD='GET' && STATUS='200,400'"
        val expected = "METHOD='GET' && $ENHANCED_FUNC_NAME('STATUS=200,400')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and STATUS expression`() {
        val expression = "METHOD='GET,POST' && STATUS='200,400'"
        val expected = "$ENHANCED_FUNC_NAME('METHOD=GET,POST') && $ENHANCED_FUNC_NAME('STATUS=200,400')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and PATH expression`() {
        val expression = "METHOD='GET,POST' || PATH='/users,/user(id:string)'"
        val expected = "$ENHANCED_FUNC_NAME('METHOD=GET,POST') || $ENHANCED_FUNC_NAME('PATH=/users,/user(id:string)')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with multiple METHOD and single PATH expression`() {
        val expression = "(METHOD='POST' && PATH='/users') || (METHOD='POST' && PATH='/products')"
        val expected = "(METHOD='POST' && PATH='/users') || (METHOD='POST' && PATH='/products')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with STATUS expression`() {
        val expression = "STATUS='2xx'"
        val expected = "$ENHANCED_FUNC_NAME('STATUS=2xx')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with PATH expression`() {
        val expression = "STATUS!=202 && PATH!='/hub,/hub/(id:string)'"
        val expected = "STATUS!=202 && $ENHANCED_FUNC_NAME('PATH!=/hub,/hub/(id:string)')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with QUERY expression`() {
        val expression = "QUERY='fields'"
        val expected = "QUERY='fields'"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with empty expression`() {
        val expression = ""
        val expected = ""
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with multiple QUERY expressions`() {
        val expression = "QUERY='name,age' && QUERY='location'"
        val expected = "$ENHANCED_FUNC_NAME('QUERY=name,age') && QUERY='location'"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with multiple HEADER expressions`() {
        val expression = "HEADER='Content-Type,Accept' && HEADER='Authorization'"
        val expected = "$ENHANCED_FUNC_NAME('HEADER=Content-Type,Accept') && HEADER='Authorization'"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with mixed operators`() {
        val expression = "METHOD='GET,POST' && STATUS!='200,400'"
        val expected = "$ENHANCED_FUNC_NAME('METHOD=GET,POST') && $ENHANCED_FUNC_NAME('STATUS!=200,400')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with no $ENHANCED_FUNCTION_NAME applicable`() {
        val expression = "METHOD='GET' && STATUS='200'"
        val expected = "METHOD='GET' && STATUS='200'"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with special characters`() {
        val expression = "PATH='/user(id:string),/user(name:string)'"
        val expected = "$ENHANCED_FUNC_NAME('PATH=/user(id:string),/user(name:string)')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression does not handle spaces around =`() {
        val expression = "METHOD = 'GET, POST' && STATUS = '200, 400'"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expression, standardExpression)
    }

    @Test
    fun `test standard expression handles spaces`() {
        val expression = "METHOD='GET, POST' && STATUS='200, 400'"
        val expected = "$ENHANCED_FUNC_NAME('METHOD=GET, POST') && $ENHANCED_FUNC_NAME('STATUS=200, 400')"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with empty $ENHANCED_FUNCTION_NAME`() {
        val expression = "METHOD='' && STATUS=''"
        val expected = "METHOD='' && STATUS=''"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }

    @Test
    fun `test standard expression with single quotes inside`() {
        val expression = "METHOD='GET' && STATUS='2'00'"
        val expected = "METHOD='GET' && STATUS='2'00'"
        val standardExpression = ScenarioMetadataFilter.standardizeExpression(expression)
        assertEquals(expected, standardExpression)
    }
}
