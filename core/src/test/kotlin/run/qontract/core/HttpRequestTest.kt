package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.pattern.*
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
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
        val value = json.getValue("form-fields") as JSONObjectValue
        assertThat(value.jsonObject.getValue("Data")).isEqualTo(StringValue("10"))
    }

    @Test
    fun `when serialised to log string, the log should contain form fields`() {
        val logString = HttpRequest("POST", "/").copy(formFields = mapOf("Data" to "10")).toLogString()

        assertThat(logString).contains("Data=10")
    }

    @Test
    fun `request with a nullable string should result in an Any null or string pattern`() {
        val request = HttpRequest("POST", "/", emptyMap(), parsedValue("""{"data": "(string?)"}"""))
        val requestPattern = request.toPattern()
        val body = requestPattern.body
        if (body !is JSONObjectPattern) fail("Expected json object pattern")

        val dataPattern = body.pattern.getValue("data") as DeferredPattern
        val nullableStringPattern = AnyPattern(listOf(NullPattern, StringPattern))

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
        assertThat(deferredBodyPattern.resolvePattern(Resolver())).isEqualTo(AnyPattern(listOf(NullPattern, StringPattern)))
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
}
