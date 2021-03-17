package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.StringValue

internal class HttpResponsePatternTest {
    @Test
    fun `it should result in 2 tests` () {
        val list = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern))).newBasedOn(Row(), Resolver())

        assertThat(list).hasSize(2)

        val flags = list.map {
            when {
                it.headersPattern.pattern.containsKey("X-Optional") -> "with"
                else -> "without"
            }
        }

        flagsContain(flags, listOf("with", "without"))
    }

    @Test
    fun `it should encompass itself`() {
        val httpResponsePattern = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern)))
        assertThat(httpResponsePattern.encompasses(httpResponsePattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another smaller response pattern`() {
        val bigger = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Required" to StringPattern)), body = toTabularPattern(mapOf("data" to AnyPattern(listOf(StringPattern, NullPattern)))))
        val smaller = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Required" to StringPattern, "X-Extra" to StringPattern)), body = toTabularPattern(mapOf("data" to StringPattern)))
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `when validating a response string against a response type number, should return an error`() {
        val response = HttpResponse(200, emptyMap(), StringValue("not a number"))
        val pattern = HttpResponsePattern(status = 200, body = NumberPattern)

        assertThat(pattern.matches(response, Resolver())).isInstanceOf(Result.Failure::class.java)
    }
}
