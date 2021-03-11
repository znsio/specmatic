package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import run.qontract.core.pattern.*
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.mock.ScenarioStub
import run.qontract.optionalPattern
import java.util.stream.Stream
import kotlin.test.assertEquals

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

        assertThatThrownBy { val json = parsedJSON(stub) as JSONObjectValue
            requestFromJSON(json.jsonObject) }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `request with a nullable string should result in an Any null or string pattern`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("""{"data": "(string?)"}"""))
        val requestPattern = request.toPattern()
        val body = requestPattern.body
        if (body !is JSONObjectPattern) fail("Expected json object pattern")

        val dataPattern = body.pattern.getValue("data") as DeferredPattern
        val nullableStringPattern = optionalPattern(StringPattern)

        assertThat(dataPattern.resolvePattern(Resolver())).isEqualTo(nullableStringPattern)
    }

    @Test
    fun `request with a string star should result in a string list pattern`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("""{"data": "(string*)"}"""))
        val requestPattern = request.toPattern()
        val body = requestPattern.body
        if (body !is JSONObjectPattern) fail("Expected json object pattern")

        val dataPattern = body.pattern.getValue("data") as DeferredPattern
        val listOfStringsPattern = ListPattern(StringPattern)

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
        assertThat(deferredPattern.resolvePattern(Resolver())).isEqualTo(RestPattern(StringPattern))
    }

    @Test
    fun `when request body is string question then converting to pattern should result in nullable string pattern as body`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("(string?)"))
        val requestPattern = request.toPattern()
        val deferredBodyPattern = requestPattern.body as DeferredPattern
        assertThat(deferredBodyPattern.resolvePattern(Resolver())).isEqualTo(optionalPattern(StringPattern))
    }

    @Test
    fun `when request body contains a nullable json key then converting to pattern should yield a body pattern with nullable key`() {
        val requestPattern = HttpRequest(
                "POST",
                "/",
                emptyMap(),
                parsedValue("""{"maybe?": "present"}""")).toPattern()

        assertThat(requestPattern.body.matches(parsedValue("""{"maybe": "present"}"""), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(requestPattern.body.matches(parsedValue("""{}"""), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `request generated by derived gherkin should be the same as original request`() {
        val request = HttpRequest("POST", path = "/square", body = parsedValue("10"))
        val featureGherkin = toGherkinFeature(NamedStub("Test", ScenarioStub(request, HttpResponse.OK(NumberValue(100)))))

        val feature = Feature(featureGherkin)
        val generatedRequest = feature.scenarios.first().generateHttpRequest()

        assertThat(generatedRequest.method).isEqualTo("POST")
        assertThat(generatedRequest.path).isEqualTo("/square")
        assertThat(generatedRequest.body).isInstanceOf(StringValue::class.java)

        val examples = exampleOf("RequestBody", "10")
        assertThat(feature.scenarios.single().examples.single()).isEqualTo(examples)
    }

    @Test
    fun `should replace the Host header value with the specified host name` () {
        val request = HttpRequest("POST", path = "/", headers = mapOf("Host" to "example.com")).withHost("newhost.com")
        assertThat(request.headers["Host"]).isEqualTo("newhost.com")
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
}
