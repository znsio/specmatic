package io.specmatic.stub

import io.specmatic.core.HttpRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UrlPrefixInterceptorTest {

    @Test
    fun `should decode path segments with path prefix`() {
        System.setProperty("PATH_PREFIX", "api/v1")
        val interceptor = UrlPrefixInterceptor()
        val request = HttpRequest(method = "GET", path = "/api/v1/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }

    @Test
    fun `should decode path segments without path prefix`() {
        System.clearProperty("PATH_PREFIX")
        val interceptor = UrlPrefixInterceptor()
        val request = HttpRequest(method = "GET", path = "/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }

    @Test
    fun `should return original path when path prefix is not a prefix`() {
        System.setProperty("PATH_PREFIX", "api/v2")
        val interceptor = UrlPrefixInterceptor()
        val request = HttpRequest(method = "GET", path = "/api/v1/products/123%20abc")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("")
    }

    @Test
    fun `should return empty path when URL does not match path prefix`() {
        System.setProperty("PATH_PREFIX", "api/v2")
        val interceptor = UrlPrefixInterceptor()
        val request = HttpRequest(method = "GET", path = "/unrelated/path")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("")
    }

    @Test
    fun `should decode default URL path`() {
        System.setProperty("PATH_PREFIX", "/")
        val interceptor = UrlPrefixInterceptor()
        val request = HttpRequest(method = "GET", path = "/api/v1/products")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/api/v1/products")
    }

    @Test
    fun `should handle empty path prefix gracefully`() {
        System.setProperty("PATH_PREFIX", "")
        val interceptor = UrlPrefixInterceptor()
        val request = HttpRequest(method = "GET", path = "/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }
}
