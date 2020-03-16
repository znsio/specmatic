package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.*

internal class HttpRequestPatternTest {
    @Test
    fun `should not match when url does not match`() {
        val httpRequestPattern = HttpRequestPattern().also {
            val urlMatcher = URLMatcher(URI("/matching_path"))
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/unmatched_path"))
        }
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(
                    Stack<String>().also { stack ->
                        stack.push("Path part did not match. Expected: matching_path Actual: unmatched_path")
                        stack.push("URL did not match")
                    }
            )
        }
    }

    @Test
    fun `should not match when method does not match`() {
        val httpRequestPattern = HttpRequestPattern().also {
            val urlMatcher = URLMatcher(URI("/matching_path"))
            it.updateMethod("POST")
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/matching_path"))
            it.setMethod("GET")
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
            val urlMatcher = URLMatcher(URI("/matching_path"))
            it.updateMethod("POST")
            it.setBodyPattern("{name: Hari}")
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/matching_path"))
            it.setMethod("POST")
            it.setBody("{unmatchedKey: unmatchedValue}")
        }
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Failure::class.java)
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push("Missing key name in {unmatchedKey=null}")
                stack.push("Request body did not match")
            })
        }
    }

    @Test
    fun `should match when request matches url, method and body`() {
        val httpRequestPattern = HttpRequestPattern().also {
            val urlMatcher = URLMatcher(URI("/matching_path"))
            it.updateMethod("POST")
            it.setBodyPattern("{name: Hari}")
            it.updateWith(urlMatcher)
        }
        val httpRequest = HttpRequest().also {
            it.updateWith(URI("/matching_path"))
            it.setMethod("POST")
            it.setBody("{name: Krishnan}")
        }
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Result.Success::class.java)
        }
    }
}