package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.StringValue
import java.net.URI
import kotlin.test.assertEquals

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
        val newPatterns = pattern.newBasedOn(Row(), Resolver())

        assertEquals(pattern, newPatterns.first())
        assertEquals(1, newPatterns.size)
    }
}