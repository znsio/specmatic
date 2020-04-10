package run.qontract.core.pattern

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.shouldMatch
import run.qontract.core.value.StringValue

internal class URLSchemeTest {
    @Test
    fun `should generate an https url`() {
        val value = parsedPattern("(url https)").generate(Resolver())

        if(value !is StringValue) fail("Expectected StringValue, got ${value.javaClass.name}")
        assertTrue(value.string.startsWith("https://"))
    }

    @Test
    fun `should generate an http url`() {
        val value = parsedPattern("(url http)").generate(Resolver())

        if(value !is StringValue) fail("Expectected StringValue, got ${value.javaClass.name}")
        assertTrue(value.string.startsWith("http://"))
    }

    @Test
    fun `should match any url`() {
        assertTrue(URLPattern(URLScheme.EITHER).matches(StringValue("https://test.com"), Resolver()) is Result.Success)
        assertTrue(URLPattern(URLScheme.EITHER).matches(StringValue("http://test.com"), Resolver()) is Result.Success)
    }

    @Test
    fun `should match an http url`() {
        assertTrue(URLPattern(URLScheme.HTTP).matches(StringValue("http://test.com"), Resolver()) is Result.Success)
    }

    @Test
    fun `should match an https url`() {
        assertTrue(URLPattern(URLScheme.HTTPS).matches(StringValue("https://test.com"), Resolver()) is Result.Success)
    }

    @Test
    fun `should match another pattern with the same scheme`() {
        assertTrue(URLPattern(URLScheme.HTTP).matchesPattern(URLPattern(URLScheme.HTTP), Resolver()))
    }

    @Test
    fun `should not match another pattern with a different scheme`() {
        assertFalse(URLPattern(URLScheme.HTTP).matchesPattern(URLPattern(URLScheme.HTTPS), Resolver()))
    }

    @Test
    fun `url path pattern should match a url path without a scheme`() {
        StringValue("/one/two") shouldMatch URLPattern(URLScheme.PATH)
    }
}
