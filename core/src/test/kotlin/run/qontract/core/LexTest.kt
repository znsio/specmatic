package run.qontract.core

import org.junit.jupiter.api.Test
import run.qontract.core.pattern.NumberTypePattern
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LexTest {
    @Test
    fun `should lex type identically to pattern`() {
        val contractGherkin = """
            Feature: Math API
            
            Scenario: Addition
              Given type Numbers (number*)
              When POST /add
              And request-body (Numbers)
              Then status 200
              And response-body (number)
        """.trimIndent()

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val request = HttpRequest().updateMethod("POST").updatePath("/add").updateBody("[1, 2]")

        val response = contractBehaviour.lookup(request)

        assertEquals(200, response.status)

        try {
            (response.body ?: "hello").toInt()
        } catch (e: Exception) { fail("${response.body} is not a number")}
    }

    @Test
    fun `should parse tabular pattern directly in request body`() {
        val contractGherkin = """
            Feature: Pet API

            Scenario: Get details
              When POST /pets
              And request-body
                | name        | (string) |
                | description | (string) |
              Then status 200
              And response-body (number)
        """.trimIndent()

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val request = HttpRequest().updateMethod("POST").updatePath("/pets").updateBody("""{"name": "Benny", "description": "Fluffy and white"}""")
        val response = contractBehaviour.lookup(request)

        try { NumberTypePattern().parse(response.body ?: "", Resolver()) } catch(e: Throwable) { fail("Expected Number value") }
    }

    @Test
    fun `should parse tabular pattern directly in response body`() {
        val contractGherkin = """
            Feature: Pet API
            
            Scenario: Get details
              When GET /pets/(id:number)
              Then status 200
              And response-body
                | id   | (number) |
                | name | (string) |
        """.trimIndent()

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val request = HttpRequest().updateMethod("GET").updatePath("/pets/10")
        val response = contractBehaviour.lookup(request)

        parsedValue(response.body ?: "").let { body ->
            if(body !is JSONObjectValue) fail("Expected JSON object")

            assertTrue(body.jsonObject.getValue("id") is NumberValue)
            assertTrue(body.jsonObject.getValue("name") is StringValue)
        }
    }

    @Test
    fun `should lex http PATCH`() {
        val contractGherkin = """
            Feature: Pet Store
            
            Scenario: Update all pets
              When PATCH /pets
              And request-body {"health": "good"}
              Then status 202
        """.trimIndent()

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val request = HttpRequest().updateMethod("PATCH").updatePath("/pets").updateBody("""{"health": "good"}""")

        val response = contractBehaviour.lookup(request)

        assertEquals(202, response.status)
    }
}