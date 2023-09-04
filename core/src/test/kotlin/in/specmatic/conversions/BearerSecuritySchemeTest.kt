package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BearerSecuritySchemeTest {
    val scheme = BearerSecurityScheme()
    @Test
    fun `authentication header starts with Bearer when using Bearer security scheme`() {
        val request = scheme.addTo(
            HttpRequest(
                method = "POST",
                path = "/customer",
            ),
        )
        assertThat(request.headers[AUTHORIZATION]).startsWith("Bearer ")
    }

    @Test
    fun `Bearer security scheme matches requests with authorization header set`() {
        val requestWithBearer = HttpRequest(method = "POST", path = "/customer", headers = mapOf(AUTHORIZATION to "Bearer foo"))
        assertThat(scheme.matches(requestWithBearer).isSuccess()).isTrue
    }

    @Test
    fun `Bearer security scheme does not matches requests with authorization header not set`() {
        val requestWithoutHeader = HttpRequest(method = "POST", path = "/customer")
        with(scheme.matches(requestWithoutHeader)) {
            assertThat(isSuccess()).isFalse
            assertThat(this.reportString()).contains("Authorization header is missing in request")
        }
    }

    @Test
    fun `Bearer security scheme does not matches requests with authorization header without bearer prefix`() {
        val requestWithoutBearer = HttpRequest(method = "POST", path = "/customer", headers = mapOf(AUTHORIZATION to "foo"))
        with(scheme.matches(requestWithoutBearer)) {
            assertThat(isSuccess()).isFalse
            assertThat(this.reportString()).contains("must be prefixed")
        }
    }
}
