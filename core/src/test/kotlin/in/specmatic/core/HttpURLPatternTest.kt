package `in`.specmatic.core

import `in`.specmatic.GENERATION
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.util.*

internal class HttpURLPatternTest {
    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only query parameters`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets?petid=(number)&owner=(string)"))
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
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets/123/owners/hari"))
        urlPattern.matches(URI("/pets/123/owners"), HashMap(), Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf("PATH"), listOf("""Expected /pets/123/owners (having 3 path segments) to match /pets/123/owners/hari (which has 4 path segments).""")))
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when query parameters do not match`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets?petid=(number)"))
        val queryParameters = mapOf("petid" to "text")

        urlPattern.matches(URI("/pets"), queryParameters, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf(QUERY_PARAMS_BREADCRUMB, "petid"), listOf("""Expected number, actual was "text"""")))
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only path parameters`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets/(petid:number)/owner/(owner:string)"))
        urlPattern.matches(URI("/pets/123123/owner/hari")).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match when all parts of the path do not match`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets/(petid:number)"))
        val queryParameters = HashMap<String, String>()
        urlPattern.matches(URI("/owners/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf("PATH (/owners/123123)"), listOf("""Expected "pets", actual was "owners"""")))
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with both path and query parameters`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets/(petid:number)?owner=(string)"))
        val queryParameters = hashMapOf("owner" to "Hari")
        urlPattern.matches(URI("/pets/123123"), queryParameters, Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    fun `should generate path when URI contains only query parameters`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets?petid=(number)"))
        urlPattern.generatePath(Resolver()).let {
            assertThat(it).isEqualTo("/pets")
        }
    }

    @Test
    fun `should generate path when url has only path parameters`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets/(petid:number)/owner/(owner:string)"))
        val resolver = mockk<Resolver>().also {
            every { it.withCyclePrevention<StringValue>(ExactValuePattern(StringValue("pets")), any())} returns StringValue("pets")
            every { it.withCyclePrevention<NumberValue>(DeferredPattern("(number)", "petid"), any())} returns NumberValue(123)
            every { it.withCyclePrevention<StringValue>(ExactValuePattern(StringValue("owner")), any())} returns StringValue("owner")
            every { it.withCyclePrevention<StringValue>(DeferredPattern("(string)", "owner"), any())} returns StringValue("hari")
        }
        urlPattern.generatePath(resolver).let{
            assertThat(it).isEqualTo("/pets/123/owner/hari")
        }
    }

    @Test
    fun `should generate query`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets?petid=(number)&owner=(string)"))
        val resolver = mockk<Resolver>().also {
            every { it.withCyclePrevention<StringValue>(ExactValuePattern(StringValue("pets")), any())} returns StringValue("pets")
            every { it.withCyclePrevention<NumberValue>(DeferredPattern("(number)", "petid"), any())} returns NumberValue(123)
            every { it.withCyclePrevention<StringValue>(DeferredPattern("(string)", "owner"), any())} returns StringValue("hari")
        }
        urlPattern.generateQuery(resolver).let {
            assertThat(it).isEqualTo(hashMapOf("petid" to "123", "owner" to "hari"))
        }
    }

    @Test
    @Tag(GENERATION)
    fun `should pick up facts`() {
        val urlPattern = toURLMatcherWithOptionalQueryParams(URI("/pets/(id:number)"))
        val resolver = Resolver(mapOf("id" to StringValue("10")))

        val newURLPatterns = urlPattern.newBasedOn(Row(), resolver)
        val path = newURLPatterns.first().generatePath(resolver)
        assertEquals("/pets/10", path)
    }

    @Test
    @Tag(GENERATION)
    fun `should create 2^n matchers on an empty Row`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?status=(string)&type=(string)"))
        val matchers = matcher.newBasedOn(Row(), Resolver())

        assertEquals(4, matchers.size)
        println(matchers)
    }

    @Test
    @Tag(GENERATION)
    fun `should generate a valid query string when there is a single row with matching columns`() {
        val row = Row(listOf("status", "type"), listOf("available", "dog"))
        val resolver = Resolver()

        val matchers = toURLMatcherWithOptionalQueryParams(URI("/pets?status=(string)&type=(string)")).newBasedOn(row, resolver)
        assertEquals(1, matchers.size)
        val query = matchers.first().generateQuery(Resolver())
        assertEquals("available", query.getValue("status"))
        assertEquals("dog", query.getValue("type"))
    }

    @Test
    fun `given a pattern in a query param, it should generate a random value matching that pattern`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?id=(string)"))
        val query = matcher.generateQuery(Resolver())

        assertNotEquals("(string)", query.getValue("id"))
        assertTrue(query.getValue("id").isNotEmpty())
    }

    @Test
    fun `request url with no query params should match a url pattern with query params`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?id=(string)"))
        assertThat(matcher.matches(URI("/pets"), emptyMap())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `request url with 1 query param should match a url pattern with superset of 2 params`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?id=(string)&name=(string)"))
        assertThat(matcher.matches(URI("/pets"), mapOf("name" to "Jack Daniel"))).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `request url query params should not match a url with unknown query params`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?id=(string)"))
        assertThat(matcher.matches(URI("/pets"), mapOf("name" to "Jack Daniel"))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a number in a query only when resolver has mock matching on`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?id=(number)"))
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "10"), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "(number)"), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "(number)"), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a boolean in a query only when resolver has mock matching on`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?available=(boolean)"))
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "true"), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "(boolean)"), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "(boolean)"), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a number in a path only when resolver has mock matching on`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets/(id:number)"))
        assertThat(matcher.matches(URI.create("/pets/10"), emptyMap(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(id:number)"), emptyMap(), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(id:number)"), emptyMap(), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a boolean in a path only when resolver has mock matching on`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets/(status:boolean)"))
        assertThat(matcher.matches(URI.create("/pets/true"), emptyMap(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(status:boolean)"), emptyMap(), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(matcher.matches(URI.create("/pets/(status:boolean)"), emptyMap(), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    @Tag(GENERATION)
    fun `should generate a path with a concrete value given a path pattern with newBasedOn`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets/(status:boolean)"))
        val matchers = matcher.newBasedOn(Row(), Resolver())
        assertThat(matchers).hasSize(1)
        assertThat(matchers.single()).isEqualTo(HttpURLPattern(emptyMap(), listOf(URLPathSegmentPattern(ExactValuePattern(StringValue("pets"))), URLPathSegmentPattern(BooleanPattern(), "status")), "/pets/(status:boolean)"))
    }

    @Test
    @Tag(GENERATION)
    fun `should generate a path with a concrete value given a query param with newBasedOn`() {
        val matcher = toURLMatcherWithOptionalQueryParams(URI("/pets?available=(boolean)"))
        val matchers = matcher.newBasedOn(Row(), Resolver())
        assertThat(matchers).hasSize(2)

        val matcherWithoutQueryParams = HttpURLPattern(emptyMap(), listOf(URLPathSegmentPattern(ExactValuePattern(StringValue("pets")))), "/pets")
        assertThat(matchers).contains(matcherWithoutQueryParams)

        val matcherWithQueryParams = HttpURLPattern(mapOf("available" to BooleanPattern()), listOf(URLPathSegmentPattern(ExactValuePattern(StringValue("pets")))), "/pets")
        assertThat(matchers).contains(matcherWithQueryParams)
    }

    @Tag(GENERATION)
    @Test
    fun `should generate negative values for a string`() {
        val urlMatchers = toURLMatcherWithOptionalQueryParams(URI("/pets?name=(string)")).negativeBasedOn(Row(), Resolver())!!
        assertThat(urlMatchers).containsExactly(HttpURLPattern(emptyMap(), listOf(URLPathSegmentPattern(ExactValuePattern(StringValue("pets")))), "/pets"))
    }

    @Tag(GENERATION)
    @Test
    fun `should generate negative values for a number`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to NumberPattern()))
        val newHeaders = headers.negativeBasedOn(Row(), Resolver())

        assertThat(newHeaders).containsExactlyInAnyOrder(
            HttpHeadersPattern(mapOf("X-TraceID" to StringPattern())),
            HttpHeadersPattern(mapOf("X-TraceID" to BooleanPattern())),
        )
    }

    @Test
    fun `url matcher with a non optional query param should not match empty query params`() {
        val matcher = HttpURLPattern(queryPatterns = mapOf("name" to StringPattern()), pathToPattern("/"), "/")

        val result = matcher.matches(URI("/"), emptyMap(), Resolver())
        assertThat(result.isSuccess()).isFalse()
    }

    @Test
    fun `url matcher with 2 non optional query params should not match a url with just one of the specified query params`() {
        val matcher = HttpURLPattern(queryPatterns = mapOf("name" to StringPattern(), "string" to StringPattern()), pathToPattern("/"), "/")

        val result = matcher.matches(URI("/"), mapOf("name" to "Archie"), Resolver())
        assertThat(result.isSuccess()).isFalse()
    }

    @Test
    fun `should stringify date time query param to date time pattern`() {
        val httpUrlPattern = HttpURLPattern(mapOf("before" to DateTimePattern), pathToPattern("/pets"), "/pets")
        assertThat(httpUrlPattern.toString()).isEqualTo("/pets?before=(datetime)")
    }
}
