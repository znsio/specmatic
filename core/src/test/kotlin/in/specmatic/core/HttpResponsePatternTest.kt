package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.StringValue

internal class HttpResponsePatternTest {
    @Test
    fun `it should encompass itself`() {
        val httpResponsePattern = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern())))
        assertThat(httpResponsePattern.encompasses(httpResponsePattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another smaller response pattern`() {
        val bigger = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Required" to StringPattern())), body = toTabularPattern(mapOf("data" to AnyPattern(listOf(StringPattern(), NullPattern)))))
        val smaller = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Extra" to StringPattern())), body = toTabularPattern(mapOf("data" to StringPattern())))
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `when validating a response string against a response type number, should return an error`() {
        val response = HttpResponse(200, emptyMap(), StringValue("not a number"))
        val pattern = HttpResponsePattern(status = 200, body = NumberPattern())

        assertThat(pattern.matches(response, Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `all response match errors should be returned together`() {
        val response = HttpResponse.ok(StringValue("not a number")).copy(headers = mapOf("X-Data" to "abc123"))
        val pattern = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Data" to NumberPattern())), body = NumberPattern())

        val result = pattern.matches(response, Resolver())

        assertThat(result).isInstanceOf(Result.Failure::class.java)

        result as Result.Failure

        assertThat(result.toMatchFailureDetailList()).hasSize(2)

        val resultText = result.reportString()
        assertThat(resultText).contains(">> RESPONSE.HEADERS.X-Data")
        assertThat(resultText).contains(">> RESPONSE.BODY")
    }

    @Test
    fun `all response backward compatibility header errors should be returned together with body errors`() {
        val older = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Data" to StringPattern())), body = StringPattern())
        val newer = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Data" to NumberPattern())), body = NumberPattern())

        val result: Result = newer.encompasses(older, Resolver(), Resolver())

        val resultText = result.reportString()

        assertThat(resultText).contains("RESPONSE.HEADER.X-Data")
        assertThat(resultText).contains("RESPONSE.BODY")
    }
}
