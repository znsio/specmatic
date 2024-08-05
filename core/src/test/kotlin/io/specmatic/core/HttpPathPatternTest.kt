package io.specmatic.core

import io.specmatic.GENERATION
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.specmatic.core.pattern.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException

internal class HttpPathPatternTest {
    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match url when number of path parts do not match`() {
        val urlPattern = buildHttpPathPattern(URI("/pets/123/owners/hari"))
        urlPattern.matches(URI("/pets/123/owners"), Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).toMatchFailureDetails()).isEqualTo(
                MatchFailureDetails(
                    listOf("PATH"),
                    listOf("""Expected /pets/123/owners (having 3 path segments) to match /pets/123/owners/hari (which has 4 path segments).""")
                )
            )
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should match url with only path parameters`() {
        val urlPattern = buildHttpPathPattern(URI("/pets/(petid:number)/owner/(owner:string)"))
        urlPattern.matches(URI("/pets/123123/owner/hari")).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    @Throws(URISyntaxException::class, UnsupportedEncodingException::class)
    fun `should not match when all parts of the path do not match`() {
        val urlPattern = buildHttpPathPattern(URI("/pets/(petid:number)"))
        urlPattern.matches(URI("/owners/123123"), Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).toMatchFailureDetails()).isEqualTo(
                MatchFailureDetails(
                    listOf("PATH (/owners/123123)"),
                    listOf("""Expected "pets", actual was "owners"""")
                )
            )
        }
    }

    @Test
    fun `should generate path when URI contains only query parameters`() {
        val urlPattern = buildHttpPathPattern(URI("/pets?petid=(number)"))
        urlPattern.generate(Resolver()).let {
            assertThat(it).isEqualTo("/pets")
        }
    }

    @Test
    fun `should generate path when url has only path parameters`() {
        val urlPattern = buildHttpPathPattern(URI("/pets/(petid:number)/owner/(owner:string)"))
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
                    ExactValuePattern(StringValue("owner")),
                    any()
                )
            } returns StringValue("owner")
            every {
                it.withCyclePrevention<StringValue>(
                    DeferredPattern("(string)", "owner"),
                    any()
                )
            } returns StringValue("hari")
        }
        urlPattern.generate(resolver).let {
            assertThat(it).isEqualTo("/pets/123/owner/hari")
        }
    }

    @Test
    @Tag(GENERATION)
    fun `should pick up facts`() {
        val urlPattern = buildHttpPathPattern(URI("/pets/(id:number)"))
        val resolver = Resolver(mapOf("id" to StringValue("10")))

        val newURLPatterns = urlPattern.newBasedOn(Row(), resolver)
        val urlPathSegmentPatterns = newURLPatterns.first()
        assertEquals(2, urlPathSegmentPatterns.size)
        val path = urlPathSegmentPatterns.joinToString("/") { it.generate(resolver).toStringLiteral() }
        assertEquals("pets/10", path)
    }

    @Test
    fun `request url with no query params should match a url pattern with query params`() {
        val matcher = buildHttpPathPattern(URI("/pets?id=(string)"))
        assertThat(matcher.matches(URI("/pets"))).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should match a number in a path only when resolver has mock matching on`() {
        val matcher = buildHttpPathPattern(URI("/pets/(id:number)"))
        assertThat(
            matcher.matches(
                URI.create("/pets/10"),
                Resolver()
            )
        ).isInstanceOf(Result.Success::class.java)
        assertThat(
            matcher.matches(
                URI.create("/pets/(id:number)"),
                Resolver(mockMode = true)
            )
        ).isInstanceOf(Result.Success::class.java)
        assertThat(
            matcher.matches(
                URI.create("/pets/(id:number)"),
                Resolver(mockMode = false)
            )
        ).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a boolean in a path only when resolver has mock matching on`() {
        val matcher = buildHttpPathPattern(URI("/pets/(status:boolean)"))
        assertThat(
            matcher.matches(
                URI.create("/pets/true"),
                Resolver()
            )
        ).isInstanceOf(Result.Success::class.java)
        assertThat(
            matcher.matches(
                URI.create("/pets/(status:boolean)"),
                Resolver(mockMode = true)
            )
        ).isInstanceOf(Result.Success::class.java)
        assertThat(
            matcher.matches(
                URI.create("/pets/(status:boolean)"),
                Resolver(mockMode = false)
            )
        ).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should return all path param errors together`() {
        val pattern = buildHttpPathPattern("/pets/(petid:number)/file/(fileid:number)")
        val mismatchResult = pattern.matches(URI("/pets/abc/file/def")) as Result.Failure

        assertThat(mismatchResult.failureReason).isNotEqualTo(FailureReason.URLPathMisMatch)
        assertThat(mismatchResult.reportString())
            .contains("PATH.petid")
            .contains("PATH.fileid")
    }

    @Test
    fun `should return failure reason as url mismatch if there is even one literal path segment mismatch`() {
        val pattern = buildHttpPathPattern("/pets/(id:number)/data")
        val mismatchResult = pattern.matches(URI("/pets/abc/info")) as Result.Failure
        assertThat(mismatchResult.failureReason).isEqualTo(FailureReason.URLPathMisMatch)
    }

    @Test
    @Tag(GENERATION)
    fun `should generate a path with a concrete value given a path pattern with newBasedOn`() {
        val matcher = buildHttpPathPattern(URI("/pets/(status:boolean)"))
        val matchers = matcher.newBasedOn(Row(), Resolver()).toList()
        assertThat(matchers).hasSize(1)
        assertThat(matchers.single()).isEqualTo(
            listOf(
                URLPathSegmentPattern(ExactValuePattern(StringValue("pets"))),
                URLPathSegmentPattern(BooleanPattern(), "status")
            )
        )
    }

    @Tag(GENERATION)
    @Test
    fun `should generate negative values for a number`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to NumberPattern()))
        val newHeaders = headers.negativeBasedOn(Row(), Resolver()).map { it.value }.toList()

        assertThat(newHeaders).containsExactlyInAnyOrder(
            HttpHeadersPattern(mapOf("X-TraceID" to StringPattern())),
            HttpHeadersPattern(mapOf("X-TraceID" to BooleanPattern())),
        )
    }
}
