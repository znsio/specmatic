package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class URLMatcherKtTest {
    @Nested
    inner class ReturnMultipleErrors {
        val urlMatcher = toURLMatcherWithOptionalQueryParams("http://example.com/?hello=(number)")
        val result = urlMatcher.matches(HttpRequest("GET", "/", queryParams = mapOf("hello" to "world", "hi" to "all")), Resolver()) as Result.Failure
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
