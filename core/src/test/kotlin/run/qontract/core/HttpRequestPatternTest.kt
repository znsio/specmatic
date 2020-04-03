package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.Row
import java.net.URI
import java.util.*
import kotlin.test.assertEquals

internal class HttpRequestPatternTest {
    @Test
    fun `should not match when url does not match`() {
        val httpRequestPattern = HttpRequestPattern().also {
            val urlMatcher = toURLPattern(URI("/matching_path"))
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/unmatched_path"))
        }
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(
                    Stack<String>().also { stack ->
                        stack.push("Expected matching_path, actual unmatched_path")
                        stack.push("Expected ExactMatchPattern(pattern=matching_path), actual unmatched_path")
                        stack.push("Path part did not match in /unmatched_path. Expected: ExactMatchPattern(pattern=matching_path) Actual: unmatched_path")
                        stack.push("URL did not match")
                    }
            )
        }
    }

    @Test
    fun `should not match when method does not match`() {
        val httpRequestPattern = HttpRequestPattern().also {
            val urlMatcher = toURLPattern(URI("/matching_path"))
            it.updateMethod("POST")
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/matching_path"))
            it.updateMethod("GET")
        }
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also{ stack ->
                stack.push("Method did not match. Expected: POST Actual: GET")
            })
        }
    }

    @Test
    fun `should not match when body does not match`() {
        val httpRequestPattern = HttpRequestPattern().also {
            val urlMatcher = toURLPattern(URI("/matching_path"))
            it.updateMethod("POST")
            it.setBodyPattern("""{"name": "Hari"}""")
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/matching_path"))
            it.updateMethod("POST")
            it.updateBody("""{"unmatchedKey": "unmatchedValue"}""")
        }
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push("Missing key name in {unmatchedKey=unmatchedValue}")
                stack.push("Request body did not match")
            })
        }
    }

    @Test
    fun `should match when request matches url, method and body`() {
        val httpRequestPattern = HttpRequestPattern().also {
            val urlMatcher = toURLPattern(URI("/matching_path"))
            it.updateMethod("POST")
            it.setBodyPattern("""{"name": "Hari"}""")
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/matching_path"))
            it.updateMethod("POST")
            it.updateBody("""{"name": "Hari"}""")
        }
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Success::class.java)
        }
    }

    @Test
    fun `a clone request pattern request should include the headers specified`() {
        val pattern = HttpRequestPattern(
                headersPattern = HttpHeadersPattern(mapOf("Test-Header" to "(string)")),
                urlPattern = toURLPattern(URI("/")),
                method = "GET"
        )

        val newPatterns = pattern.newBasedOn(Row(), Resolver())
        assertEquals("(string)", newPatterns[0].headersPattern.headers.get("Test-Header"))
    }
}