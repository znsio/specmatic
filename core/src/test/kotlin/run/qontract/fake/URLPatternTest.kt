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
import run.qontract.core.toURLPattern
import run.qontract.core.pattern.LookupPattern
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.util.*

internal class URLPatternTest {
    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only query parameters`() {
        val urlMatcher = toURLPattern(URI("/pets?petid=(number)&owner=(string)"))
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
        val urlMatcher = toURLPattern(URI("/pets/123/owners/hari"))
        urlMatcher.matches(URI("/pets/123/owners"), HashMap(), Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(
                    Stack<String>().also { stack ->
                        stack.push("Expected /pets/123/owners to have 4 parts, but it has 3 parts.")
                    }
            )
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when query parameters do not match`() {
        val urlMatcher = toURLPattern(URI("/pets?petid=(number)"))
        val queryParameters = mapOf("petid" to "text")

        urlMatcher.matches(URI("/pets"), queryParameters, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(
                    Stack<String>().also { stack ->
                        stack.push("\"text\" is not a Number")
                        stack.push("Expected (number), actual text")
                        stack.push("Expected LookupPattern(pattern=(number), key=petid), actual text")
                        stack.push("Query parameter did not match")
                    }
            )
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only path parameters`() {
        val urlMatcher = toURLPattern(URI("/pets/(petid:number)/owner/(owner:string)"))
        urlMatcher.matches(URI("/pets/123123/owner/hari")).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match when all parts of the path do not match`() {
        val urlMatcher = toURLPattern(URI("/pets/(petid:number)"))
        val queryParameters = HashMap<String, String>()
        urlMatcher.matches(URI("/owners/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push("Expected pets, actual owners")
                stack.push("Expected ExactMatchPattern(pattern=pets), actual owners")
                stack.push("Path part did not match in /owners/123123. Expected: ExactMatchPattern(pattern=pets) Actual: owners")
            })
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with both path and query parameters`() {
        val urlMatcher = toURLPattern(URI("/pets/(petid:number)?owner=(string)"))
        val queryParameters = hashMapOf("owner" to "Hari")
        urlMatcher.matches(URI("/pets/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    fun `should generate path when URI contains only query parameters`() {
        val urlMatcher = toURLPattern(URI("/pets?petid=(number)"))
        urlMatcher.generatePath(Resolver()).let {
            assertThat(it).isEqualTo("/pets")
        }
    }

    @Test
    fun `should generate path when url has only path parameters`() {
        val urlMatcher = toURLPattern(URI("/pets/(petid:number)/owner/(owner:string)"))
        val resolver = mockk<Resolver>().also {
            every { it.generate("petid", LookupPattern("(number)", "petid")) } returns NumberValue(123)
            every { it.generate("owner", LookupPattern("(string)", "owner")) } returns StringValue("hari")
        }
        urlMatcher.generatePath(resolver).let{
            assertThat(it).isEqualTo("/pets/123/owner/hari")
        }
    }

    @Test
    fun `should generate query`() {
        val urlMatcher = toURLPattern(URI("/pets?petid=(number)&owner=(string)"))
        val resolver = mockk<Resolver>().also {
            every { it.generate("petid", LookupPattern("(number)", "petid")) } returns NumberValue(123)
            every { it.generate("owner", LookupPattern("(string)", "owner")) } returns StringValue("hari")
        }
        urlMatcher.generateQuery(resolver).let {
            assertThat(it).isEqualTo(hashMapOf("petid" to "123", "owner" to "hari"))
        }
    }
}