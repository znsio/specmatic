package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.*
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

        val response = contractBehaviour.lookupResponse(request)

        assertEquals(200, response.status)

        try {
            (response.body ?: StringValue("NaNaNaNaNaNaNaNaNaNaNaNaNaNaNaNa Batman")).displayableValue().toInt()
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
        val response = contractBehaviour.lookupResponse(request)

        assertThat(response.body).isInstanceOf(NumberValue::class.java)
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
        val response = contractBehaviour.lookupResponse(request)

        response.body?.let { body ->
            if(body !is JSONObjectValue) fail("Expected JSON object")

            assertTrue(body.jsonObject.getValue("id") is NumberValue)
            assertTrue(body.jsonObject.getValue("name") is StringValue)
        } ?: fail("Response body was null")
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

        val response = contractBehaviour.lookupResponse(request)

        assertEquals(202, response.status)
    }

    @Test
    fun `should parse multipart file spec`() {
        val behaviour = ContractBehaviour("""
            Feature: Customer Data API
            
            Scenario: Upload customer information
              When POST /data
              And request-part customer_info @customer_info.csv text/csv gzip
              Then status 200
        """.trimIndent())

        val pattern = behaviour.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single() as MultiPartFilePattern
        assertThat(pattern.name).isEqualTo("customer_info")
        assertThat(pattern.filename).isEqualTo("@customer_info.csv")
        assertThat(pattern.contentType).isEqualTo("text/csv")
        assertThat(pattern.contentEncoding).isEqualTo("gzip")
    }

    @Test
    fun `should parse multipart file spec without content encoding`() {
        val behaviour = ContractBehaviour("""
            Feature: Customer Data API
            
            Scenario: Upload customer information
              When POST /data
              And request-part customer_info @customer_info.csv text/csv
              Then status 200
        """.trimIndent())

        val pattern = behaviour.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single() as MultiPartFilePattern
        assertThat(pattern.name).isEqualTo("customer_info")
        assertThat(pattern.filename).isEqualTo("@customer_info.csv")
        assertThat(pattern.contentType).isEqualTo("text/csv")
        assertThat(pattern.contentEncoding).isEqualTo(null)
    }

    @Test
    fun `should parse multipart file spec without content type and content encoding`() {
        val behaviour = ContractBehaviour("""
            Feature: Customer Data API
            
            Scenario: Upload customer information
              When POST /data
              And request-part customer_info @customer_info.csv
              Then status 200
        """.trimIndent())

        val pattern = behaviour.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single() as MultiPartFilePattern
        assertThat(pattern.name).isEqualTo("customer_info")
        assertThat(pattern.filename).isEqualTo("@customer_info.csv")
        assertThat(pattern.contentType).isEqualTo(null)
        assertThat(pattern.contentEncoding).isEqualTo(null)
    }

    @Test
    fun `should parse multipart content spec`() {
        val behaviour = ContractBehaviour("""
            Feature: Customer Data API
            
            Scenario: Upload multipart info
              Given json Customer
                | customerId | (number) |
              And json Order
                | orderId | (number) |
              When POST /data
              And request-part customer_info (Customer)
              And request-part order_info (Order)
              Then status 200
        """.trimIndent())

        val patterns = behaviour.scenarios.single().httpRequestPattern.multiPartFormDataPattern.map { it as MultiPartContentPattern }

        val resolver = Resolver(newPatterns = behaviour.scenarios.single().patterns)

        assertThat(patterns[0].name).isEqualTo("customer_info")
        val pattern0 = deferredToJsonPatternData(patterns[0].content, resolver)
        val contentPattern0 = deferredToNumberPattern(pattern0.getValue("customerId"), resolver)
        assertThat(contentPattern0).isEqualTo(NumberPattern)

        assertThat(patterns[1].name).isEqualTo("order_info")
        val pattern1 = deferredToJsonPatternData(patterns[1].content, resolver)
        val contentPattern1 = deferredToNumberPattern(pattern1.getValue("orderId"), resolver)
        assertThat(contentPattern1).isEqualTo(NumberPattern)
    }

    @Test
    fun `should parse a row lookup pattern`() {
        val behaviour = ContractBehaviour("""
            Feature: Customer Data API

            Scenario: Upload multipart info
              Given json Data
                | id1 | (number from customerId) |
                | id2 | (number from orderId)    |
              When POST /data
              And request-body (Data)
              Then status 200

              Examples:
              | customerId | orderId |
              | 10         | 20      |
        """.trimIndent())

        val pattern = behaviour.scenarios.single().patterns.getValue("(Data)") as TabularPattern

        assertThat(pattern.pattern.getValue("id1")).isEqualTo(LookupRowPattern(DeferredPattern("(number)"), "customerId"))
        assertThat(pattern.pattern.getValue("id2")).isEqualTo(LookupRowPattern(DeferredPattern("(number)"), "orderId"))
    }

    private fun deferredToJsonPatternData(pattern: Pattern, resolver: Resolver): Map<String, Pattern> =
            ((pattern as DeferredPattern).resolvePattern(resolver) as TabularPattern).pattern

    private fun deferredToNumberPattern(pattern: Pattern, resolver: Resolver): NumberPattern =
            (pattern as DeferredPattern).resolvePattern(resolver) as NumberPattern

    private fun resolveDeferred(pattern: Pattern, resolver: Resolver): Pattern =
            (pattern as DeferredPattern).resolvePattern(resolver)
}
