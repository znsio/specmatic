package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.Row
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.util.*
import kotlin.collections.HashMap
import kotlin.test.assertEquals

internal class HttpHeadersPatternTest {
    @Test
    fun `should exact match`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to "value"))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "value"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should pattern match a numeric string`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to "(number)"))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "123"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should pattern match string`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to "(string)"))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "abc123"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should not pattern match a numeric string when value has alphabets`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to "(number)"))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "abc"
        httpHeaders.matches(headers, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push(""""abc" is not a Number""")
                stack.push("""Expected (number), actual abc""")
                stack.push("""Header "key" did not match""")
            })
        }
    }

    @Test
    fun `should not match when header is not present`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to "(number)"))
        val headers: HashMap<String, String> = HashMap()
        headers["anotherKey"] = "123"
        httpHeaders.matches(headers, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push("""Header "key" was not available""")
            })
        }
    }

    @Test
    fun `should not add numericString pattern to the resolver`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to "(string)"))
        val resolver = Resolver()
        httpHeaders.matches(HashMap(), resolver)
        assertThat(resolver.matchesPattern(null, resolver.getPattern("(number)"), StringValue("123")) is Result.Failure).isTrue()
    }

    @Test
    fun `should generate values`() {
        val httpHeaders = HttpHeadersPattern(
                mapOf("exactKey" to "value", "numericKey" to "(number)", "stringKey" to "(string)", "serverStateKey" to "(string)"))
        val facts: HashMap<String, Value> = hashMapOf("serverStateKey" to StringValue("serverStateValue"))
        val resolver = Resolver(facts)
        val generatedResult = httpHeaders.generate(resolver)
        generatedResult.let {
            assertThat(it["exactKey"]).isEqualTo("value")
            assertThat(it["numericKey"]).matches("[0-9]+")
            assertThat(it["stringKey"]).matches("[0-9,a-z,A-Z]+")
            assertThat(it["serverStateKey"]).isEqualTo("serverStateValue")
        }
    }

    @Test
    fun `should not attempt to validate or match additional headers`() {
        val expectedHeaders = HttpHeadersPattern(mapOf("Content-Type" to "(string)"))

        val actualHeaders = HashMap<String, String>().apply {
            put("Content-Type", "application/json")
            put("X-Unspecified-Header", "We don't care")
        }

        assertThat(expectedHeaders.matches(actualHeaders, Resolver()).isTrue()).isTrue()
    }

    @Test
    fun `should generate new header objects given a row`() {
        val headers = HttpHeadersPattern(mapOf("Content-Type" to "(string)"))
        val newHeaders = headers.newBasedOn(Row())
        assertEquals("(string)", newHeaders[0].headers.getValue("Content-Type"))
    }
}