package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.Row
import run.qontract.core.pattern.StringPattern
import run.qontract.core.pattern.parsedPattern
import run.qontract.core.pattern.stringToPattern
import java.net.URI
import kotlin.test.assertEquals

internal class HttpRequestPatternTest {
    @Test
    fun `should not match when url does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLPattern(URI("/matching_path")))
        val httpRequest = HttpRequest().updateWith(URI("/unmatched_path"))
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "URL", "PATH (/unmatched_path)"), listOf("""Expected string: "matching_path", actual was string: "unmatched_path"""")))
        }
    }

    @Test
    fun `should not match when method does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLPattern(URI("/matching_path")),
                method = "POST")
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("GET")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "METHOD"), listOf("Expected POST, actual was GET")))
        }
    }

    @Test
    fun `should not match when body does not match`() {
        val httpRequestPattern =
                HttpRequestPattern(
                        urlMatcher = toURLPattern(URI("/matching_path")),
                        method = "POST",
                        body = parsedPattern("""{"name": "Hari"}"""))
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("POST")
            .updateBody("""{"unmatchedKey": "unmatchedValue"}""")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "BODY"), listOf("Missing key name")))
        }
    }

    @Test
    fun `should match when request matches url, method and body`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher =  toURLPattern(URI("/matching_path")),
                method = "POST",
                body = parsedPattern("""{"name": "Hari"}"""))
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("POST")
            .updateBody("""{"name": "Hari"}""")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Success::class.java)
        }
    }

    @Test
    fun `a clone request pattern request should include the headers specified`() {
        val pattern = HttpRequestPattern(
                headersPattern = HttpHeadersPattern(mapOf("Test-Header" to stringToPattern("(string)", "Test-Header"))),
                urlMatcher = toURLPattern(URI("/")),
                method = "GET"
        )

        val newPatterns = pattern.newBasedOn(Row(), Resolver())
        assertEquals("(string)", newPatterns[0].headersPattern.pattern.get("Test-Header").toString())
    }

    @Test
    fun `a request with an optional header should result in 2 options for newBasedOn`() {
        val requests = HttpRequestPattern(method = "GET",
                urlMatcher = toURLPattern(URI("/")),
                headersPattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern))).newBasedOn(Row(), Resolver())

        assertThat(requests).hasSize(2)

        val flags = requests.map {
            when {
                it.headersPattern.pattern.containsKey("X-Optional") -> "with"
                else -> "without"
            }
        }

        flagsContain(flags, listOf("with", "without"))
    }
}
