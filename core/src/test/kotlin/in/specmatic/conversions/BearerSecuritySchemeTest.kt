package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BearerSecuritySchemeTest {
    @Test
    fun `authentication header starts with Bearer when using Bearer security scheme`() {
        val scheme = BearerSecurityScheme()
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
        val scheme = BearerSecurityScheme()

        val requestWithBearer = HttpRequest(method = "POST", path = "/customer", headers = mapOf(AUTHORIZATION to "Bearer foo"))
        assertThat(scheme.matches(requestWithBearer).isSuccess()).isTrue

        val requestWithoutHeader = HttpRequest(method = "POST", path = "/customer")
        with(scheme.matches(requestWithoutHeader)) {
            assertThat(isSuccess()).isFalse
            assertThat(this.reportString()).contains("Authorization header is missing in request")
        }

        val requestWithoutBearer = HttpRequest(method = "POST", path = "/customer", headers = mapOf(AUTHORIZATION to "foo"))
        with(scheme.matches(requestWithoutBearer)) {
            assertThat(isSuccess()).isFalse
            assertThat(this.reportString()).contains("must be prefixed")
        }
    }
}
