package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.NumberPattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import `in`.specmatic.core.pattern.stringToPattern
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.collections.HashMap

internal class HttpHeadersPatternTest {
    @Test
    fun `should exact match`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("value", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "value"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should pattern match a numeric string`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "123"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should pattern match string`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(string)", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "abc123"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should not pattern match a numeric string when value has alphabets`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "abc"
        httpHeaders.matches(headers, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("HEADERS", "key"), listOf("Expected number, actual was string: \"abc\"")))
        }
    }

    @Test
    fun `should not match when header is not present`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["anotherKey"] = "123"
        httpHeaders.matches(headers, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report())
                    .isEqualTo(FailureReport(listOf("HEADERS"), listOf("Expected header named \"key\" was missing")))
        }
    }

    @Test
    fun `should not add numericString pattern to the resolver`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key")))
        val resolver = Resolver()
        httpHeaders.matches(HashMap(), resolver)
        assertThat(resolver.matchesPattern(null, resolver.getPattern("(number)"), StringValue("123")) is Result.Failure).isTrue()
    }

    @Test
    fun `should generate values`() {
        val httpHeaders = HttpHeadersPattern(
                mapOf("exactKey" to stringToPattern("value", "exactKey"), "numericKey" to stringToPattern("(number)", "numericKey"), "stringKey" to stringToPattern("(string)", "stringKey"), "serverStateKey" to stringToPattern("(string)", "serverStateKey")))
        val facts: HashMap<String, Value> = hashMapOf("serverStateKey" to StringValue("serverStateValue"))
        val resolver = Resolver(facts)
        val generatedResult = httpHeaders.generate(resolver)
        generatedResult.let {
            assertThat(it["exactKey"]).isEqualTo("value")
            assertThat(it["numericKey"]).matches("[0-9]+")
            assertThat(it["stringKey"]).matches("[0-9a-zA-Z]+")
            assertThat(it["serverStateKey"]).isEqualTo("serverStateValue")
        }
    }

    @Test
    fun `should not attempt to validate or match additional headers`() {
        val expectedHeaders = HttpHeadersPattern(mapOf("Content-Type" to stringToPattern("(string)", "Content-Type")))

        val actualHeaders = HashMap<String, String>().apply {
            put("Content-Type", "application/json")
            put("X-Unspecified-Header", "Should be ignored")
        }

        assertThat(expectedHeaders.matches(actualHeaders, Resolver()).isTrue()).isTrue()
    }

    @Test
    fun `should validate extra headers when mocking`() {
        val expectedHeaders = HttpHeadersPattern(mapOf("X-Expected" to stringToPattern("(string)", "X-Expected")))

        val actualHeaders = HashMap<String, String>().apply {
            put("X-Expected", "application/json")
            put("X-Unspecified-Header", "Can't accept this header in a mock")
        }

        assertThat(expectedHeaders.matches(actualHeaders, Resolver(findKeyError = checkAllKeys))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should generate new header objects given a row`() {
        val headers = HttpHeadersPattern(mapOf("Content-Type" to stringToPattern("(string)", "Content-Type")))
        val newHeaders = headers.newBasedOn(Row(), Resolver())
        assertEquals("(string)", newHeaders[0].pattern.getValue("Content-Type").toString())
    }

    @Test
    fun `given ancestor headers from the creators header set the pattern should skip past all headers in the value map not in the ancestors list`() {
        val pattern = HttpHeadersPattern(mapOf("X-Required-Header" to StringPattern()), mapOf("X-Required-Header" to StringPattern()))
        val headers = mapOf("X-Required-Header" to "some value", "X-Extraneous-Header" to "some other value")

        assertThat(pattern.matches(headers, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should match if optional headers are present`() {
        val pattern = HttpHeadersPattern(mapOf("X-Optional-Header?" to StringPattern()))
        val headers = mapOf("X-Optional-Header?" to "some value")

        assertThat(pattern.matches(headers, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should match if optional headers are absent`() {
        val pattern = HttpHeadersPattern(mapOf("X-Optional-Header?" to StringPattern()))
        val headers = emptyMap<String, String>()

        assertThat(pattern.matches(headers, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `an optional header should result in 2 new header patterns for newBasedOn`() {
        val pattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern()))
        val list = pattern.newBasedOn(Row(), Resolver())

        assertThat(list).hasSize(2)

        val flags = list.map {
            when {
                it.pattern.contains("X-Optional") -> "with"
                else -> "without"
            }
        }

        flagsContain(flags, listOf("with", "without"))
    }

    @Test
    fun `it should encompass itself`() {
        val headersType = HttpHeadersPattern(mapOf("X-Required" to StringPattern()))
        assertThat(headersType.encompasses(headersType, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `a header pattern with required headers should encompass one with extra headers`() {
        val bigger = HttpHeadersPattern(mapOf("X-Required" to StringPattern()))
        val smaller = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Extra" to StringPattern()))
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `a header pattern with an optional header should match one without that header`() {
        val bigger = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Optional?" to NumberPattern()))
        val smaller = HttpHeadersPattern(mapOf("X-Required" to StringPattern()))
        val result = bigger.encompasses(smaller, Resolver(), Resolver())
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `a header pattern with an optional header should match one with that header if present`() {
        val bigger = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Optional?" to NumberPattern()))
        val smaller = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Optional" to StringPattern()))
        val result = bigger.encompasses(smaller, Resolver(), Resolver())
        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a pattern only when resolver has mock matching on`() {
        val headersPattern = HttpHeadersPattern(mapOf("X-Data" to NumberPattern()))
        assertThat(headersPattern.matches(mapOf("X-Data" to "10"), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(headersPattern.matches(mapOf("X-Data" to "(number)"), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(headersPattern.matches(mapOf("X-Data" to "(number)"), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `unexpected standard http headers are allowed and will not break a match check`() {
        val headersPattern = HttpHeadersPattern(mapOf("X-Data" to StringPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Content-Type?" to StringPattern()))
        val resolver = Resolver(findKeyError = checkAllKeys)
        assertThat(headersPattern.matches(mapOf("X-Data" to "data", "Content-Type" to "text/plain"), resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `unexpected but non-optional standard http headers are allowed and will break a match check`() {
        val headersPattern = HttpHeadersPattern(mapOf("X-Data" to StringPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Content-Type" to StringPattern()))
        val resolver = Resolver(findKeyError = checkAllKeys)
        assertThat(headersPattern.matches(mapOf("X-Data" to "data", "Content-Type" to "text/plain"), resolver)).isInstanceOf(Result.Failure::class.java)
    }
}
