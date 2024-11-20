package io.specmatic.stub

import io.specmatic.core.HttpRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UrlDecodeInterceptorTest {

    @Test
    fun `should decode path segments when URL contains schema`() {
        val interceptor = UrlDecodeInterceptor()
        val request = HttpRequest(method = "GET", path = "https://example.com/products/Electronics%3FMobile")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("https://example.com/products/Electronics?Mobile")
    }

    @Test
    fun `should decode path segments when URL does not contain schema`() {
        val interceptor = UrlDecodeInterceptor()
        val request = HttpRequest(method = "GET", path = "/products/123%3Fabc")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123?abc")
    }

    @Test
    fun `should not modify path if there is nothing to decode`() {
        val interceptor = UrlDecodeInterceptor()
        val request = HttpRequest(method = "GET", path = "/products/123abc")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123abc")
    }

    @Test
    fun `should return empty path when given empty path`() {
        val interceptor = UrlDecodeInterceptor()
        val request = HttpRequest(method = "GET", path = "")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("")
    }
}
