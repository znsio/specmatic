package run.qontract.fake

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.URLMatcher
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.util.*

internal class URLMatcherTest {
    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only query parameters`() {
        val urlMatcher = URLMatcher(URI("/pets?petid=(number)&owner=(string)"))
        val queryParameters = hashMapOf(
                "petid" to "123123",
                "owner" to "hari"
        )
        urlMatcher.matches(URI("/pets"), queryParameters, Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when number of path parts do not match`() {
        val urlMatcher = URLMatcher(URI("/pets/123/owners/hari"))
        urlMatcher.matches(URI("/pets/123/owners"), HashMap(), Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(
                    Stack<String>().also { stack ->
                        stack.push("Number path parts do not match. Expected: 4 Actual: 3")
                    }
            )
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when query parameters do not match`() {
        val urlMatcher = URLMatcher(URI("/pets?petid=(number)"))
        val queryParameters = hashMapOf("petid" to "text")
        val resolver = mockk<Resolver>(relaxed = true).also {
            every { it.matchesPatternValue("petid", "(number)", "text") } returns Result.Failure("resolver error message")
        }
        urlMatcher.matches(URI("/pets"), queryParameters, resolver).let { it ->
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(
                    Stack<String>().also { stack ->
                        stack.push("resolver error message")
                        stack.push("Query parameter did not match")
                    }
            )
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only path parameters`() {
        val urlMatcher = URLMatcher(URI("/pets/(petid:number)/owner/(owner:string)"))
        val queryParameters = HashMap<String, String>()
        urlMatcher.matches(URI("/pets/123123/owner/hari"), queryParameters, Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match when all parts of the path do not match`() {
        val urlMatcher = URLMatcher(URI("/pets/(petid:number)"))
        val queryParameters = HashMap<String, String>()
        urlMatcher.matches(URI("/owners/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push("Path part did not match. Expected: pets Actual: owners")
            })
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with both path and query parameters`() {
        val urlMatcher = URLMatcher(URI("/pets/(petid:number)?owner=(string)"))
        val queryParameters = hashMapOf("owner" to "Hari")
        urlMatcher.matches(URI("/pets/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    fun `should generate path when URI contains only query parameters`() {
        val urlMatcher = URLMatcher(URI("/pets?petid=(number)"))
        val resolver = mockk<Resolver>()
        urlMatcher.generatePath(resolver).let {
            assertThat(it).isEqualTo("/pets")
        }
        verify(exactly = 0) { resolver.generateFromAny(any(), any()) }
    }

    @Test
    fun `should generate path when url has only path parameters`() {
        val urlMatcher = URLMatcher(URI("/pets/(petid:number)/owner/(owner:string)"))
        val resolver = mockk<Resolver>().also {
            every { it.generateFromAny("petid", "(number)") } returns NumberValue(123)
            every { it.generateFromAny("owner", "(string)") } returns StringValue("hari")
        }
        urlMatcher.generatePath(resolver).let{
            assertThat(it).isEqualTo("/pets/123/owner/hari")
        }
    }

    @Test
    fun `should generate query`() {
        val urlMatcher = URLMatcher(URI("/pets?petid=(number)&owner=(string)"))
        val resolver = mockk<Resolver>().also {
            every { it.generateValue("petid", "(number)") } returns NumberValue(123)
            every { it.generateValue("owner", "(string)") } returns StringValue("hari")
        }
        urlMatcher.generateQuery(resolver).let {
            assertThat(it).isEqualTo(hashMapOf("petid" to "123", "owner" to "hari"))
        }
    }
}