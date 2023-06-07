package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BearerSecuritySchemeTest {
    @Test
    fun `authentication header starts with Bearer when using Bearer secuirty scheme`() {
        val scheme = BearerSecurityScheme()
        val request = scheme.addTo(
            HttpRequest(
                method = "POST",
                path = "/customer",
            ),
        )
        assertThat(request.headers[AUTHORIZATION]).startsWith("Bearer ")
    }
}
