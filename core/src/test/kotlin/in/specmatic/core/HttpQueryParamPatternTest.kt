package `in`.specmatic.core

import `in`.specmatic.GENERATION
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException

class HttpQueryParamPatternTest {
    @Test
    fun `request url query params should not match a url with unknown query params`() {
        val matcher = buildQueryPattern(URI("/pets?id=(string)"))
        assertThat(matcher.matches(URI("/pets"), mapOf("name" to "Jack Daniel"))).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `should match a boolean in a query only when resolver has mock matching on`() {
        val matcher = buildQueryPattern(URI("/pets?available=(boolean)"))
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "true"), Resolver())).isInstanceOf(Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "(boolean)"), Resolver(mockMode = true))).isInstanceOf(
            Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("available" to "(boolean)"), Resolver(mockMode = false))).isInstanceOf(
            Failure::class.java)
    }

    @Test
    fun `url matcher with a mandatory query param should not match empty query params`() {
        val matcher = HttpQueryParamPattern(mapOf("name" to StringPattern()))
        val result = matcher.matches(URI("/"), emptyMap(), Resolver())
        assertThat(result.isSuccess()).isFalse()
    }

    @Test
    fun `should match a number in a query only when resolver has mock matching on`() {
        val matcher = buildQueryPattern(URI("/pets?id=(number)"))
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "10"), Resolver())).isInstanceOf(Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "(number)"), Resolver(mockMode = true))).isInstanceOf(
            Success::class.java)
        assertThat(matcher.matches(URI.create("/pets"), mapOf("id" to "(number)"), Resolver(mockMode = false))).isInstanceOf(
            Failure::class.java)
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when query parameters do not match`() {
        val urlPattern = buildQueryPattern(URI("/pets?petid=(number)"))
        val queryParameters = mapOf("petid" to "text")

        urlPattern.matches(URI("/pets"), queryParameters, Resolver()).let {
            assertThat(it is Failure).isTrue()
            assertThat((it as Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf(QUERY_PARAMS_BREADCRUMB, "petid"), listOf("""Expected number, actual was "text"""")))
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only query parameters`() {
        val urlPattern = buildQueryPattern(URI("/pets?petid=(number)&owner=(string)"))
        val queryParameters = hashMapOf(
            "petid" to "123123",
            "owner" to "hari"
        )
        urlPattern.matches(URI("/pets"), queryParameters, Resolver()).let {
            assertThat(it is Success).isTrue()
        }
    }

    @Test
    fun `request url with 1 query param should match a url pattern with superset of 2 params`() {
        val matcher = buildQueryPattern(URI("/pets?id=(string)&name=(string)"))
        assertThat(
            matcher.matches(
                URI("/pets"),
                mapOf("name" to "Jack Daniel")
            )
        ).isInstanceOf(Success::class.java)
    }

    @Test
    fun `should generate query`() {
        val urlPattern = buildQueryPattern(URI("/pets?petid=(number)&owner=(string)"))
        val resolver = mockk<Resolver>().also {
            every {
                it.withCyclePrevention<StringValue>(
                    ExactValuePattern(StringValue("pets")),
                    any()
                )
            } returns StringValue("pets")
            every {
                it.withCyclePrevention<NumberValue>(
                    DeferredPattern("(number)", "petid"),
                    any()
                )
            } returns NumberValue(123)
            every {
                it.withCyclePrevention<StringValue>(
                    DeferredPattern("(string)", "owner"),
                    any()
                )
            } returns StringValue("hari")
        }
        urlPattern.generate(resolver).let {
            assertThat(it).isEqualTo(hashMapOf("petid" to "123", "owner" to "hari"))
        }
    }

    @Test
    @Tag(GENERATION)
    fun `should generate a valid query string when there is a single row with matching columns`() {
        val resolver = Resolver()
        val row = Row(listOf("status", "type"), listOf("available", "dog"))
        val generatedPatterns = buildQueryPattern(URI("/pets?status=(string)&type=(string)")).newBasedOn(row, resolver)
        assertEquals(1, generatedPatterns.size)
        val pattern = HttpQueryParamPattern(generatedPatterns.first()).generate(resolver)
        assertEquals("available", pattern.getValue("status"))
        assertEquals("dog", pattern.getValue("type"))
    }

    @Test
    fun `given a pattern in a query param, it should generate a random value matching that pattern`() {
        val matcher = buildQueryPattern(URI("/pets?id=(string)"))
        val query = matcher.generate(Resolver())

        Assertions.assertNotEquals("(string)", query.getValue("id"))
        assertTrue(query.getValue("id").isNotEmpty())
    }

    @Test
    fun `url matcher with 2 non optional query params should not match a url with just one of the specified query params`() {
        val matcher =
            HttpQueryParamPattern(queryPatterns = mapOf("name" to StringPattern(), "string" to StringPattern()))

        val result = matcher.matches(HttpRequest(queryParams = mapOf("name" to "Archie")), Resolver())
            .breadCrumb(QUERY_PARAMS_BREADCRUMB)
        assertThat(result.isSuccess()).isFalse()
    }

    @Test
    fun `should stringify date time query param to date time pattern`() {
        val httpQueryParamPattern = HttpQueryParamPattern(mapOf("before" to DateTimePattern))
        assertThat(httpQueryParamPattern.toString()).isEqualTo("?before=(datetime)")
    }

    @Test
    @Tag(GENERATION)
    fun `should generate a path with a concrete value given a query param with newBasedOn`() {
        val matcher = buildQueryPattern(URI("/pets?available=(boolean)"))
        val matchers = matcher.newBasedOn(Row(), Resolver())
        assertThat(matchers).hasSize(2)
        assertThat(matchers).contains(emptyMap())
        assertThat(matchers).contains(mapOf("available" to BooleanPattern()))
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with both path and query parameters`() {
        val urlPattern = buildQueryPattern(URI("/pets/(petid:number)?owner=(string)"))
        val queryParameters = hashMapOf("owner" to "Hari")
        urlPattern.matches(URI("/pets/123123"), queryParameters, Resolver()).let {
            assertThat(it is Success).isTrue()
        }
    }

    @Tag(GENERATION)
    @Test
    fun `should generate negative values for a string`() {
        val urlMatchers = buildQueryPattern(URI("/pets?name=(string)")).negativeBasedOn(Row(), Resolver())
        assertThat(urlMatchers).containsExactly(emptyMap())
    }

    @Test
    @Tag(GENERATION)
    fun `should create 2^n matchers on an empty Row`() {
        val patterns = buildQueryPattern(URI("/pets?status=(string)&type=(string)"))
        val generatedPatterns = patterns.newBasedOn(Row(), Resolver())
        assertThat(generatedPatterns).containsExactlyInAnyOrder(
            emptyMap(),
            mapOf("status" to StringPattern()),
            mapOf("type" to StringPattern()),
            mapOf("status" to StringPattern(), "type" to StringPattern()),
        )
    }

    @Test
    fun `should correctly stringize a url matching having a query param with an array type`() {
        val matcher = HttpQueryParamPattern(mapOf("data" to CsvPattern(NumberPattern())))
        assertThat(matcher.toString()).isEqualTo("?data=(csv/number)")
    }

    @Nested
    inner class ReturnMultipleErrors {
        val urlMatcher = buildQueryPattern(URI.create("http://example.com/?hello=(number)"))
        val result = urlMatcher.matches(HttpRequest("GET", "/", queryParams = mapOf("hello" to "world", "hi" to "all")), Resolver()) as Failure
        val resultText = result.toReport().toText()

        @Test
        fun `should return as many errors as there are value mismatches`() {
            assertThat(result.toMatchFailureDetailList()).hasSize(2)
        }

        @Test
        fun `keys with errors should be present in the error list`() {
            assertThat(resultText).contains(">> QUERY-PARAMS.hello")
            assertThat(resultText).contains(">> QUERY-PARAMS.hi")
        }

        @Test
        fun `key presence errors should appear before value errors`() {
            assertThat(resultText.indexOf(">> QUERY-PARAMS.hi")).isLessThan(resultText.indexOf(">> QUERY-PARAMS.hello"))
        }
    }
}