package `in`.specmatic.core

import `in`.specmatic.core.HttpRequest.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.optionalPattern
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals

internal class HttpRequestTest {
    @Test
    fun `it should serialise the request correctly`() {
        val request = HttpRequest("GET", "/", HashMap(), EmptyString, HashMap(mapOf("one" to "two")))
        val expectedString = """GET /?one=two

"""

        assertEquals(expectedString, request.toLogString(""))
    }

    @Test
    fun `when serialised to json, the request should contain form fields`() {
        val json = HttpRequest("POST", "/").copy(formFields = mapOf("Data" to "10")).toJSON()
        val value = json.jsonObject.getValue("form-fields") as JSONObjectValue
        assertThat(value.jsonObject.getValue("Data")).isEqualTo(StringValue("10"))
    }

    @Test
    fun `when serialised to log string, the log should contain form fields`() {
        val logString = HttpRequest("POST", "/").copy(formFields = mapOf("Data" to "10")).toLogString()

        assertThat(logString).contains("Data=10")
    }

    @Test
    fun `when converting from stub a null body should be interpreted as an empty string`() {
        val stub = """
            {
              "http-request": {
                "method": "POST",
                "path": "/",
                "body": null
              },
              
              "http-response": {
                "method": "
              }
            }
        """.trimIndent()

        assertThatThrownBy {
            val json = parsedJSON(stub) as JSONObjectValue
            requestFromJSON(json.jsonObject)
        }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `request with a nullable string should result in an Any null or string pattern`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("""{"data": "(string?)"}"""))
        val requestPattern = request.toPattern()
        val body = requestPattern.body
        if (body !is JSONObjectPattern) fail("Expected json object pattern")

        val dataPattern = body.pattern.getValue("data") as DeferredPattern
        val nullableStringPattern = optionalPattern(StringPattern())

        assertThat(dataPattern.resolvePattern(Resolver())).isEqualTo(nullableStringPattern)
    }

    @Test
    fun `request with a string star should result in a string list pattern`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("""{"data": "(string*)"}"""))
        val requestPattern = request.toPattern()
        val body = requestPattern.body
        if (body !is JSONObjectPattern) fail("Expected json object pattern")

        val dataPattern = body.pattern.getValue("data") as DeferredPattern
        val listOfStringsPattern = ListPattern(StringPattern())

        assertThat(dataPattern.resolvePattern(Resolver())).isEqualTo(listOfStringsPattern)
    }

    @Test
    fun `converting to pattern with request with array containing string rest should result in a string rest pattern`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("""{"data": ["(string...)"]}"""))
        val requestPattern = request.toPattern()
        val body = requestPattern.body

        if (body !is JSONObjectPattern) fail("Expected json object pattern")

        val dataPattern = body.pattern.getValue("data") as JSONArrayPattern
        val deferredPattern = dataPattern.pattern.first() as DeferredPattern
        assertThat(deferredPattern.resolvePattern(Resolver())).isEqualTo(RestPattern(StringPattern()))
    }

    @Test
    fun `when request body is string question then converting to pattern should result in nullable string pattern as body`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("(string?)"))
        val requestPattern = request.toPattern()
        val deferredBodyPattern = requestPattern.body as DeferredPattern
        assertThat(deferredBodyPattern.resolvePattern(Resolver())).isEqualTo(optionalPattern(StringPattern()))
    }

    @Test
    fun `when request body contains a nullable json key then converting to pattern should yield a body pattern with nullable key`() {
        val requestPattern = HttpRequest(
            "POST",
            "/",
            emptyMap(),
            parsedValue("""{"maybe?": "present"}""")
        ).toPattern()

        assertThat(
            requestPattern.body.matches(
                parsedValue("""{"maybe": "present"}"""),
                Resolver()
            )
        ).isInstanceOf(Result.Success::class.java)
        assertThat(
            requestPattern.body.matches(
                parsedValue("""{}"""),
                Resolver()
            )
        ).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `request generated by derived gherkin should be the same as original request`() {
        val request = HttpRequest("POST", path = "/square", body = parsedValue("10"))
        val featureGherkin =
            toGherkinFeature(NamedStub("Test", ScenarioStub(request, HttpResponse.OK(NumberValue(100)))))

        val feature = parseGherkinStringToFeature(featureGherkin)
        val generatedRequest = feature.scenarios.first().generateHttpRequest()

        assertThat(generatedRequest.method).isEqualTo("POST")
        assertThat(generatedRequest.path).isEqualTo("/square")
        assertThat(generatedRequest.body).isInstanceOf(StringValue::class.java)

        val examples = exampleOf("RequestBody", "10")
        assertThat(feature.scenarios.single().examples.single()).isEqualTo(examples)
    }

    @Test
    fun `should replace the Host header value with the specified host name`() {
        val request = HttpRequest("POST", path = "/", headers = mapOf("Host" to "example.com")).withHost("newhost.com")
        assertThat(request.headers["Host"]).isEqualTo("newhost.com")
    }

    @Test
    fun `should exclude dynamic headers`() {
        HttpRequest(
            "POST",
            path = "/",
            headers = mapOf("Authorization" to "Bearer DummyToken")
        ).withoutDynamicHeaders().headers.let {
            assertThat(it).isEmpty()
        }
    }

    private fun exampleOf(columnName: String, value: String): Examples {
        val columns = listOf(columnName)
        return Examples(columns, listOf(Row(columns, listOf(value))))
    }

    companion object {
        @JvmStatic
        fun urlFragments(): Stream<Arguments> =
            listOf(
                Arguments.of("http://localhost/", "/test"),
                Arguments.of("http://localhost", "test"),
                Arguments.of("http://localhost/", "test"),
                Arguments.of("http://localhost", "/test"),
                Arguments.of("", "http://localhost/test"),
                Arguments.of("http://localhost/test", ""),
                Arguments.of(null, "http://localhost/test"),
            ).stream()
    }

    @ParameterizedTest
    @MethodSource("urlFragments")
    fun `it should handle an extra slash between base and path gracefully`(baseUrl: String?, path: String) {
        println("baseUrl: $baseUrl")
        println("path: $path")

        val url = HttpRequest("GET", path).getURL(baseUrl)

        assertThat(url).isEqualTo("http://localhost/test")
    }

    @Test
    fun `it should handle single query param`() {
        val url = HttpRequest("GET", "/", queryParams = mapOf("A" to "B")).getURL("http://localhost/test")
        assertThat(url).isEqualTo("http://localhost/test?A=B")
    }

    @Test
    fun `it should handle multiple query params`() {
        val url = HttpRequest("GET", "/", queryParams = mapOf("A" to "B", "C" to "D")).getURL("http://localhost/test")
        assertThat(url).isEqualTo("http://localhost/test?A=B&C=D")
    }

    @Test
    fun `it should handle URL encoding for query params`() {
        val url =
            HttpRequest("GET", "/", queryParams = mapOf("A" to "B E", "©C" to "!D")).getURL("http://localhost/test")
        assertThat(url).isEqualTo("http://localhost/test?A=B+E&%C2%A9C=%21D")
    }

    @Test
    fun `override the host header to exclude the port suffix when the default port is used`() {
        val builderWithPort80 = HttpRequestBuilder().apply {
            this.url.host = "test.com"
            this.url.port = 80
        }
        HttpRequest("GET", "/").buildRequest(builderWithPort80, null)
        assertThat(builderWithPort80.headers.get("Host")).isEqualTo("test.com")

        val httpRequestBuilderWithHTTPS = HttpRequestBuilder().apply {
            this.url.protocol = URLProtocol.HTTPS
            this.url.host = "test.com"
            this.url.port = 443
        }
        HttpRequest("GET", "/").buildRequest(httpRequestBuilderWithHTTPS, null)
        assertThat(httpRequestBuilderWithHTTPS.headers.get("Host")).isEqualTo("test.com")
    }

    @Test
    fun `by default do not override the host header unless a default port is used`() {
        val httpRequestBuilder2 = HttpRequestBuilder().apply {
            this.url.host = "test.com"
            this.url.port = 8080
        }
        HttpRequest("GET", "/").buildRequest(httpRequestBuilder2, null)
        assertThat(httpRequestBuilder2.headers.get("Host")).isNull()
    }

    @Test
    fun `should formulate a loggable error in non-strict mode`() {
        val request = spyk(
            HttpRequest(
                "POST",
                "/test",
                headers = mapOf("SOAPAction" to "test")
            )
        )
        every { request.requestNotRecognized(any<LenientRequestNotRecognizedMessages>()) }.returns("msg")

        request.requestNotRecognized()

        verify { request.requestNotRecognized(any<LenientRequestNotRecognizedMessages>()) }
    }

    @Test
    fun `should formulate a loggable error in strict mode`() {
        val request = spyk(
            HttpRequest(
                "POST",
                "/test",
                headers = mapOf("SOAPAction" to "test")
            )
        )
        every { request.requestNotRecognized(any<StrictRequestNotRecognizedMessages>()) }.returns("msg")

        request.requestNotRecognized()

        verify { request.requestNotRecognized(any<StrictRequestNotRecognizedMessages>()) }
    }

    @Test
    fun `should formulate a loggable error message describing the SOAP request without strict mode`() {
        assertThat(
            LenientRequestNotRecognizedMessages().soap(
                "test",
                "/test"
            )
        ).isEqualTo("No matching SOAP stub or contract found for SOAPAction test and path /test")
    }

    @Test
    fun `should formulate a loggable error message describing the XML-REST request without strict mode`() {
        assertThat(
            LenientRequestNotRecognizedMessages().xmlOverHttp(
                "POST",
                "/test"
            )
        ).isEqualTo("No matching XML-REST stub or contract found for method POST and path /test (assuming you're looking for a REST API since no SOAPAction header was detected)")
    }

    @Test
    fun `should formulate a loggable error message describing the JSON-REST request without strict mode`() {
        assertThat(
            LenientRequestNotRecognizedMessages().restful(
                "POST",
                "/test"
            )
        ).isEqualTo("No matching REST stub or contract found for method POST and path /test (assuming you're looking for a REST API since no SOAPAction header was detected)")
    }

    @Test
    fun `should formulate a loggable error message describing the SOAP request in strict mode`() {
        assertThat(
            StrictRequestNotRecognizedMessages().soap(
                "test",
                "/test"
            )
        ).isEqualTo("No matching SOAP stub (strict mode) found for SOAPAction test and path /test")
    }

    @Test
    fun `should formulate a loggable error message describing the XML-REST request in strict mode`() {
        assertThat(
            StrictRequestNotRecognizedMessages().xmlOverHttp(
                "POST",
                "/test"
            )
        ).isEqualTo("No matching XML-REST stub (strict mode) found for method POST and path /test (assuming you're looking for a REST API since no SOAPAction header was detected)")
    }

    @Test
    fun `should formulate a loggable error message describing the JSON-REST request in strict mode`() {
        assertThat(
            StrictRequestNotRecognizedMessages().restful(
                "POST",
                "/test"
            )
        ).isEqualTo("No matching REST stub (strict mode) found for method POST and path /test (assuming you're looking for a REST API since no SOAPAction header was detected)")
    }
}
