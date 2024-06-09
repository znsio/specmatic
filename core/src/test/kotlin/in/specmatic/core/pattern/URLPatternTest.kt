package `in`.specmatic.core.pattern

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals

internal class URLPatternTest {
    @Test
    fun `should be able to match a url`() {
        val url = StringValue("http://test.com")
        val pattern = URLPattern(URLScheme.HTTP)

        url shouldMatch pattern
    }

    @Test
    fun `should not match a url with the wrong scheme`() {
        val url = StringValue("http://test.com")
        val pattern = URLPattern(URLScheme.HTTPS)

        url shouldNotMatch pattern
    }

    @Test
    fun `should generate a url`() {
        val pattern = URLPattern(URLScheme.HTTPS)
        val url = pattern.generate(Resolver())

        try { URI.create(url.string) } catch (e: Throwable) { fail("${url.string} was not a URL.")}
    }

    @Test
    fun `should return itself when generating a new url based on a row`() {
        val pattern = URLPattern(URLScheme.HTTPS)
        val newPatterns = pattern.newBasedOnR(Row(), Resolver()).toList().map { it.value }

        assertEquals(pattern, newPatterns.first())
        assertEquals(1, newPatterns.size)
    }
}