package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.GherkinSection.*
import run.qontract.core.HttpResponse.Companion.OK
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue

internal class HttpRequestKtTest {
    @Test
    fun `json body should end up as a JSONObjectValue object`() {
        val jsonRequest = """
            {
                "method": "POST",
                "path": "/",
                "body": {
                    "a": 1
                }
            }
        """.trimIndent()

        val request = requestFromJSON(jsonStringToValueMap(jsonRequest))
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/")
        assertThat(request.body).isInstanceOf(JSONObjectValue::class.java)

        val body = (request.body as JSONObjectValue).jsonObject

        val valueOfA = body.getValue("a")
        assertThat(valueOfA).isInstanceOf(NumberValue::class.java)
        assertThat(valueOfA.toStringValue()).isEqualTo("1")
    }

    @Test
    fun `gherkin clauses from request with number body`() {
        val request = HttpRequest("POST", "/data", emptyMap(), StringValue("10"))
        val (clauses, _) = toGherkinClauses(request)

        assertThat(clauses).hasSize(2)

        assertThat(clauses.first().section).isEqualTo(When)
        assertThat(clauses.first().content).isEqualTo("POST /data")

        assertThat(clauses[1].section).isEqualTo(When)
        assertThat(clauses[1].content).isEqualTo("request-body (string from RequestBody)")
    }

    @Test
    fun `gherkin clauses from request with query`() {
        val request = HttpRequest("GET", "/data", queryParams = mapOf("count" to "1"))
        val (clauses, _) = toGherkinClauses(request)

        assertThat(clauses).hasSize(1)

        assertThat(clauses.first().section).isEqualTo(When)
        assertThat(clauses.first().content).isEqualTo("GET /data?count=(number)")
    }

    @Test
    fun `gherkin clauses from request with headers`() {
        val request = HttpRequest("POST", "/data", mapOf("X-Custom" to "data"), EmptyString)
        val (clauses, _) = toGherkinClauses(request)

        assertThat(clauses).hasSize(2)

        assertThat(clauses.first().section).isEqualTo(When)
        assertThat(clauses.first().content).isEqualTo("POST /data")

        assertThat(clauses[1].section).isEqualTo(When)
        assertThat(clauses[1].content).isEqualTo("request-header X-Custom (string)")
    }

    @Test
    fun `gherkin clauses from request with form fields`() {
        val request = HttpRequest("POST", "/data", formFields = mapOf("field" to "10"))
        val (clauses, _) = toGherkinClauses(request)

        assertThat(clauses).hasSize(2)

        assertThat(clauses.first().section).isEqualTo(When)
        assertThat(clauses.first().content).isEqualTo("POST /data")

        assertThat(clauses[1].section).isEqualTo(When)
        assertThat(clauses[1].content).isEqualTo("form-field field (number)")
    }

    @Test
    fun `gherkin clauses from request with form data`() {
        val request = HttpRequest("POST", "/data", multiPartFormData = listOf(MultiPartContentValue("field", NumberValue(20))))
        val (clauses, _) = toGherkinClauses(request)

        assertThat(clauses).hasSize(2)

        assertThat(clauses.first().section).isEqualTo(When)
        assertThat(clauses.first().content).isEqualTo("POST /data")

        assertThat(clauses[1].section).isEqualTo(When)
        assertThat(clauses[1].content).isEqualTo("request-part field (number)")
    }

    @Test
    fun `query param pattern value should not be added as an example`() {
        val request = HttpRequest(method = "POST", path = "/customer", queryParams = mapOf("key" to "(string)"))

        val (clauses, examples) = toGherkinClauses(request)
        assertThat(clauses.first().content).isEqualTo("POST /customer?key=(string)")
        assertThat(examples.examples).isEmpty()
    }

    @Test
    fun `header pattern value should not be added as an example`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = mapOf("key" to "(string)"))

        val (clauses, examples) = toGherkinClauses(request)
        assertThat(clauses[0].content).isEqualTo("POST /customer")
        assertThat(clauses[1].content).isEqualTo("request-header key (string)")
        assertThat(examples.examples).isEmpty()
    }

    @Test
    fun `form field value should not be added as an example`() {
        val request = HttpRequest(method = "POST", path = "/customer", formFields = mapOf("key" to "(string)"))

        val (clauses, examples) = toGherkinClauses(request)
        assertThat(clauses[0].content).isEqualTo("POST /customer")
        assertThat(clauses[1].content).isEqualTo("form-field key (string)")
        assertThat(examples.examples).isEmpty()
    }

    @Test
    fun `form data value should not be added as an example`() {
        val request = HttpRequest(method = "POST", path = "/customer", multiPartFormData = listOf(MultiPartContentValue("key", StringValue("(string)"))))

        val (clauses, examples) = toGherkinClauses(request)
        assertThat(clauses[0].content).isEqualTo("POST /customer")
        assertThat(clauses[1].content).isEqualTo("request-part key (string)")
        assertThat(examples.examples).isEmpty()
    }
}
