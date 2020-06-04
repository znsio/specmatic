package run.qontract.core

import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.Row
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class URLMatcherTest {
    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only query parameters`() {
        val urlPattern = toURLPattern(URI("/pets?petid=(number)&owner=(string)"))
        val queryParameters = hashMapOf(
                "petid" to "123123",
                "owner" to "hari"
        )
        urlPattern.matches(URI("/pets"), queryParameters, Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when number of path parts do not match`() {
        val urlPattern = toURLPattern(URI("/pets/123/owners/hari"))
        urlPattern.matches(URI("/pets/123/owners"), HashMap(), Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("PATH"), listOf("""Expected /pets/123/owners (having 3 path segments) to match /pets/123/owners/hari (which has 4 path segments).""")))
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when query parameters do not match`() {
        val urlPattern = toURLPattern(URI("/pets?petid=(number)"))
        val queryParameters = mapOf("petid" to "text")

        urlPattern.matches(URI("/pets"), queryParameters, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("QUERY-PARAMS", "petid"), listOf("""Expected number, actual was string: "text"""")))
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only path parameters`() {
        val urlPattern = toURLPattern(URI("/pets/(petid:number)/owner/(owner:string)"))
        urlPattern.matches(URI("/pets/123123/owner/hari")).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match when all parts of the path do not match`() {
        val urlPattern = toURLPattern(URI("/pets/(petid:number)"))
        val queryParameters = HashMap<String, String>()
        urlPattern.matches(URI("/owners/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("PATH (/owners/123123)"), listOf("""Expected string: "pets", actual was string: "owners"""")))
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with both path and query parameters`() {
        val urlPattern = toURLPattern(URI("/pets/(petid:number)?owner=(string)"))
        val queryParameters = hashMapOf("owner" to "Hari")
        urlPattern.matches(URI("/pets/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    fun `should generate path when URI contains only query parameters`() {
        val urlPattern = toURLPattern(URI("/pets?petid=(number)"))
        urlPattern.generatePath(Resolver()).let {
            assertThat(it).isEqualTo("/pets")
        }
    }

    @Test
    fun `should generate path when url has only path parameters`() {
        val urlPattern = toURLPattern(URI("/pets/(petid:number)/owner/(owner:string)"))
        val resolver = mockk<Resolver>().also {
            every { it.generate("petid", DeferredPattern("(number)", "petid")) } returns NumberValue(123)
            every { it.generate("owner", DeferredPattern("(string)", "owner")) } returns StringValue("hari")
        }
        urlPattern.generatePath(resolver).let{
            assertThat(it).isEqualTo("/pets/123/owner/hari")
        }
    }

    @Test
    fun `should generate query`() {
        val urlPattern = toURLPattern(URI("/pets?petid=(number)&owner=(string)"))
        val resolver = mockk<Resolver>().also {
            every { it.generate("petid", DeferredPattern("(number)", "petid")) } returns NumberValue(123)
            every { it.generate("owner", DeferredPattern("(string)", "owner")) } returns StringValue("hari")
        }
        urlPattern.generateQuery(resolver).let {
            assertThat(it).isEqualTo(hashMapOf("petid" to "123", "owner" to "hari"))
        }
    }

    @Test
    fun `should pick up facts`() {
        val urlPattern = toURLPattern(URI("/pets/(id:number)"))
        val resolver = Resolver(mapOf("id" to StringValue("10")))

        val newURLPatterns = urlPattern.newBasedOn(Row(), resolver)
        val path = newURLPatterns.first().generatePath(resolver)
        assertEquals("/pets/10", path)
    }

    @Test
    fun `should create 2^n matchers on an empty Row`() {
        val matcher = toURLPattern(URI("/pets?status=(string)&type=(string)"))
        val matchers = matcher.newBasedOn(Row(), Resolver())

        assertEquals(4, matchers.size)
        println(matchers)
    }

    @Test
    fun `should generate a valid query string when there is a single row with matching columns`() {
        val row = Row(listOf("status", "type"), listOf("available", "dog"))
        val resolver = Resolver()

        val matchers = toURLPattern(URI("/pets?status=(string)&type=(string)")).newBasedOn(row, resolver)
        assertEquals(1, matchers.size)
        val query = matchers.first().generateQuery(Resolver())
        assertEquals("available", query.getValue("status"))
        assertEquals("dog", query.getValue("type"))
    }

    @Test
    fun `given a pattern in a query param, it should generate a random value matching that pattern`() {
        val matcher = toURLPattern(URI("/pets?id=(string)"))
        val query = matcher.generateQuery(Resolver())

        assertNotEquals("(string)", query.getValue("id"))
        assertTrue(query.getValue("id").isNotEmpty())
    }

    @Test
    fun `request url with no query params should match a url pattern with query params`() {
        val matcher = toURLPattern(URI("/pets?id=(string)"))
        assertThat(matcher.matches(URI("/pets"), emptyMap())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `request url with 1 query param should match a url pattern with superset of 2 params`() {
        val matcher = toURLPattern(URI("/pets?id=(string)&name=(string)"))
        assertThat(matcher.matches(URI("/pets"), mapOf("name" to "Jack Daniel"))).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `request url query params should match a url with unknown query params`() {
        val matcher = toURLPattern(URI("/pets?id=(string)"))
        assertThat(matcher.matches(URI("/pets"), mapOf("name" to "Jack Daniel"))).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should match a number in a query only when resolver has mock matching on`() {
        val matcher = toURLPattern(URI("/pets?id=(number)"))
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "10"), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "(number)"), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "(number)"), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a boolean in a query only when resolver has mock matching on`() {
        val matcher = toURLPattern(URI("/pets?available=(boolean)"))
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "true"), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "(boolean)"), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "(boolean)"), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a number in a path only when resolver has mock matching on`() {
        val matcher = toURLPattern(URI("/pets/(id:number)"))
        assertThat(matcher.matches(URI.create("/pets/10"), emptyMap(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(id:number)"), emptyMap(), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(id:number)"), emptyMap(), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a boolean in a path only when resolver has mock matching on`() {
        val matcher = toURLPattern(URI("/pets/(status:boolean)"))
        assertThat(matcher.matches(URI.create("/pets/true"), emptyMap(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(status:boolean)"), emptyMap(), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(status:boolean)"), emptyMap(), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun temp() {

    }
}
