package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import `in`.specmatic.core.GherkinSection.Then
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import java.util.*
import kotlin.math.exp
import kotlin.test.assertEquals

internal class HttpResponseTest {
    @Test
    fun createANewResponseObjectWithInitialValues() {
        val response = HttpResponse(500, "ERROR", HashMap())
        Assertions.assertEquals(500, response.status)
        Assertions.assertEquals(StringValue("ERROR"), response.body)
    }

    @Test
    fun createANewResponseObjectWithoutInitialValues() {
        val response = HttpResponse.EMPTY
        Assertions.assertEquals(0, response.status)
        Assertions.assertEquals(EmptyString, response.body)
    }

    @Test
    fun `updating body with value should automatically set Content-Type header`() {
        HttpResponse.EMPTY.updateBodyWith(parsedValue("""{"name": "John Doe"}""")).let {
            val responseBody = it.body

            if(responseBody !is JSONObjectValue)
                throw AssertionError("Expected responseBody to be a JSON object, but got ${responseBody.javaClass.name}")

            assertEquals("John Doe", responseBody.jsonObject.getValue("name").toStringValue())
            assertEquals("application/json", it.headers.getOrDefault("Content-Type", ""))
        }
    }

    @Test
    fun `gherkin clauses from simple 200 response`() {
        val clauses = toGherkinClauses(HttpResponse.OK)

        assertThat(clauses.first).hasSize(1)
        assertThat(clauses.first.single().section).isEqualTo(Then)
        assertThat(clauses.first.single().content).isEqualTo("status 200")
    }

    @Test
    fun `gherkin clauses from response with headers`() {
        val clauses = toGherkinClauses(HttpResponse(200, headers = mapOf("X-Value" to "10"), body = EmptyString))

        assertThat(clauses.first).hasSize(2)
        assertThat(clauses.first.first().section).isEqualTo(Then)
        assertThat(clauses.first.first().content).isEqualTo("status 200")

        assertThat(clauses.first[1].section).isEqualTo(Then)
        assertThat(clauses.first[1].content).isEqualTo("response-header X-Value (number)")
    }

    @Test
    fun `gherkin clauses from response with body`() {
        val clauses = toGherkinClauses(HttpResponse(200, headers = emptyMap(), body = StringValue("response data")))

        assertThat(clauses.first).hasSize(2)
        assertThat(clauses.first.first().section).isEqualTo(Then)
        assertThat(clauses.first.first().content).isEqualTo("status 200")

        assertThat(clauses.first[1].section).isEqualTo(Then)
        assertThat(clauses.first[1].content).isEqualTo("response-body (string)")
    }

    @Test
    fun `gherkin clauses from response with number body`() {
        val clauses = toGherkinClauses(HttpResponse(200, headers = emptyMap(), body = StringValue("10")))

        assertThat(clauses.first).hasSize(2)
        assertThat(clauses.first.first().section).isEqualTo(Then)
        assertThat(clauses.first.first().content).isEqualTo("status 200")

        assertThat(clauses.first[1].section).isEqualTo(Then)
        assertThat(clauses.first[1].content).isEqualTo("response-body (number)")
    }

    @Test
    fun `gherkin clauses should contain no underscores when there are duplicate keys`() {
        val (clauses, _, examples) = toGherkinClauses(HttpResponse(200, body = parsedJSON("""[{"data": 1}, {"data": 2}]""")))

        assertThat(examples).isInstanceOf(DiscardExampleDeclarations::class.java)

        for(clause in clauses) {
            assertThat(clause.content).doesNotContain("_")
        }
    }

    @Test
    fun `response-body selector with no path should return response body`() {
        val response = HttpResponse.OK("hello")
        testSelection(response, "response-body", "hello")
    }

    private fun testSelection(response: HttpResponse, selector: String, expectedValue: String) {
        val selectedValue = response.selectValue(selector)
        assertThat(selectedValue).isEqualTo(expectedValue)
    }

    @Test
    fun `response-body selector with a path should return the JSON value at that path`() {
        val response = HttpResponse.OK(JSONObjectValue(mapOf("token" to NumberValue(10))))
        testSelection(response, "response-body.token", "10")
    }

    @Test
    fun `response-body selector with a path that points to a JSON object should return it`() {
        val nameData = mapOf("name" to StringValue("Jack"))
        val responseBody = JSONObjectValue(mapOf("person" to JSONObjectValue(nameData)))

        val response = HttpResponse.OK(responseBody)
        val selectedValue = response.selectValue("response-body.person")
        val parsedValue = parsedValue(selectedValue)

        assertThat(parsedValue).isEqualTo(JSONObjectValue(nameData))
    }

    @Test
    fun `response-header selector with a path should return the JSON value at that path`() {
        val response = HttpResponse.OK.copy(headers = mapOf("Token" to "abc123"))
        testSelection(response, "response-header.Token", "abc123")
    }

    @Test
    fun `exports bindings`() {
        val response = HttpResponse.OK(JSONObjectValue(mapOf("token" to NumberValue(10))))
        val bindings = response.export(mapOf("token" to "response-body.token"))
        assertThat(bindings).isEqualTo(mapOf("token" to "10"))
    }

    @Test
    fun `throws error if export is not found`() {
        val response = HttpResponse.OK(JSONObjectValue(mapOf("token" to NumberValue(10))))
        assertThatThrownBy { response.export(mapOf("token" to "response-body.notfound")) }.isInstanceOf(ContractException::class.java)
    }
}