package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import java.net.URI
import kotlin.test.assertEquals

internal class HttpRequestPatternTest {
    @Test
    fun `should not match when url does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLMatcher(URI("/matching_path")))
        val httpRequest = HttpRequest().updateWith(URI("/unmatched_path"))
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "URL", "PATH (/unmatched_path)"), listOf("""Expected string: "matching_path", actual was string: "unmatched_path"""")))
        }
    }

    @Test
    fun `should not match when method does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLMatcher(URI("/matching_path")),
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
                        urlMatcher = toURLMatcher(URI("/matching_path")),
                        method = "POST",
                        body = parsedPattern("""{"name": "Hari"}"""))
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("POST")
            .updateBody("""{"unmatchedKey": "unmatchedValue"}""")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "BODY"), listOf("Expected key name was missing")))
        }
    }

    @Test
    fun `should match when request matches url, method and body`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher =  toURLMatcher(URI("/matching_path")),
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
                urlMatcher = toURLMatcher(URI("/")),
                method = "GET"
        )

        val newPatterns = pattern.newBasedOn(Row(), Resolver())
        assertEquals("(string)", newPatterns[0].headersPattern.pattern.get("Test-Header").toString())
    }

    @Test
    fun `a request with an optional header should result in 2 options for newBasedOn`() {
        val requests = HttpRequestPattern(method = "GET",
                urlMatcher = toURLMatcher(URI("/")),
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

    @Test
    fun `number bodies should match numerical strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = NumberPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("10"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `boolean bodies should match boolean strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = BooleanPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("true"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `boolean bodies should not match non-boolean strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = BooleanPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("10"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `integer bodies should not match non-integer strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = NumberPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("not a number"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `request with multiple parts and no optional values should result in just one test for the whole`() {
        val parts = listOf(MultiPartContentPattern("data1", StringPattern), MultiPartContentPattern("data2", StringPattern))
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = parts)
        val patterns = requestPattern.newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(1)

        assertThat(patterns.single().multiPartFormDataPattern).isEqualTo(parts)
    }

    @Test
    fun `request with an optional part should result in two requests`() {
        val part = MultiPartContentPattern("data1?", StringPattern)

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(2)

        assertThat(patterns).contains(HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = emptyList()))
        assertThat(patterns).contains(HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part.nonOptional())))
    }
}
