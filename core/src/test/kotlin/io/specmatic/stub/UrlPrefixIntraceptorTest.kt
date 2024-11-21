package io.specmatic.stub

import io.specmatic.core.HttpRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UrlPrefixInterceptorTest {

    @Test
    fun `should decode path segments with PATH_PREFIX`() {
        System.setProperty("PATH_PREFIX", "api/v1")
        val interceptor = UrlPrefixInterceptor(null)
        val request = HttpRequest(method = "GET", path = "/api/v1/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }

    @Test
    fun `should decode path segments with serverUrlFromOpenSpecs`() {
        System.clearProperty("PATH_PREFIX")
        val interceptor = UrlPrefixInterceptor("https://example.com/api/v1")
        val request = HttpRequest(method = "GET", path = "/api/v1/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }

    @Test
    fun `should prioritize PATH_PREFIX over serverUrlFromOpenSpecs`() {
        System.setProperty("PATH_PREFIX", "api/v2")
        val interceptor = UrlPrefixInterceptor("https://example.com/api/v1")
        val request = HttpRequest(method = "GET", path = "/api/v2/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }

    @Test
    fun `should return empty path when neither PATH_PREFIX nor serverUrlFromOpenSpecs matches`() {
        System.setProperty("PATH_PREFIX", "api/v2")
        val interceptor = UrlPrefixInterceptor("https://example.com/api/v3")
        val request = HttpRequest(method = "GET", path = "/api/v1/products/123%20abc")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("")
    }

    @Test
    fun `should return original path when PATH_PREFIX is null and serverUrlFromOpenSpecs does not match`() {
        System.clearProperty("PATH_PREFIX")
        val interceptor = UrlPrefixInterceptor("https://example.com/api/v2")
        val request = HttpRequest(method = "GET", path = "/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("")
    }

    @Test
    fun `should handle default URL path when PATH_PREFIX is root`() {
        System.setProperty("PATH_PREFIX", "/")
        val interceptor = UrlPrefixInterceptor(null)
        val request = HttpRequest(method = "GET", path = "/api/v1/products")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/api/v1/products")
    }

    @Test
    fun `should handle empty PATH_PREFIX gracefully`() {
        System.setProperty("PATH_PREFIX", "")
        val interceptor = UrlPrefixInterceptor(null)
        val request = HttpRequest(method = "GET", path = "/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }

    @Test
    fun `should decode path segments using both PATH_PREFIX and serverUrlFromOpenSpecs`() {
        System.setProperty("PATH_PREFIX", "api/v1")
        val interceptor = UrlPrefixInterceptor("https://example.com/api/v1")
        val request = HttpRequest(method = "GET", path = "/api/v1/products/123")
        val interceptedRequest = interceptor.interceptRequest(request)
        assertThat(interceptedRequest.path).isEqualTo("/products/123")
    }
}
