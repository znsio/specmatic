package integration_tests

import io.specmatic.core.HttpRequest
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.stub.createStubFromContracts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartialExampleTest {
    @Test
    fun `stub should load an example for a spec with constrained path param`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_in_response_body.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("POST", "/person", body = parsedJSONObject("""{"name": "Stan", "department": "engineering"}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Mumbai")
        }
    }
}