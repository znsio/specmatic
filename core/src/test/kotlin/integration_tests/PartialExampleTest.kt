package integration_tests

import io.specmatic.core.HttpRequest
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.stub.createStubFromContracts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartialExampleTest {
    @Test
    fun `stub should load an example for a spec with constrained path param`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/spec_with_path_param_with_constraint.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/users/100")
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.findFirstChildByPath("id")).isEqualTo(NumberValue(10))
        }
    }
}