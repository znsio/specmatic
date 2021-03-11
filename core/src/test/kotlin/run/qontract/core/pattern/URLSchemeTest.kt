package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.StringValue
import run.qontract.shouldMatch

internal class URLSchemeTest {
    @Test
    fun `should generate an https url`() {
        val value = parsedPattern("(url-https)").generate(Resolver())

        if(value !is StringValue) fail("Expectected StringValue, got ${value.javaClass.name}")
        assertTrue(value.string.startsWith("https://"))
    }

    @Test
    fun `should generate an http url`() {
        val value = parsedPattern("(url-http)").generate(Resolver())

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
        assertThat(URLPattern(URLScheme.HTTP).encompasses(URLPattern(URLScheme.HTTP), Resolver(), Resolver())).isInstanceOf(
            Result.Success::class.java)
    }

    @Test
    fun `should not match another pattern with a different scheme`() {
        assertThat(URLPattern(URLScheme.HTTP).encompasses(URLPattern(URLScheme.HTTPS), Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `url path pattern should match a url path without a scheme`() {
        StringValue("/one/two") shouldMatch URLPattern(URLScheme.PATH)
    }

    @Test
    fun `url path pattern should match a url path without a scheme or trailing prefix`() {
        StringValue("one/two") shouldMatch URLPattern(URLScheme.PATH)
    }

    @Test
    fun `url path pattern should match a path that does not end with a tld`() {
        assertFalse(URLPattern(URLScheme.PATH).generate(Resolver()).string.endsWith(".com"))
    }
}
