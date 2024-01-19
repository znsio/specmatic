package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiArrayQueryParameterTest {

    @Test
    fun `should generate request with multiple values for an array query parameter`() {
        val contract = OpenApiSpecification.fromFile("src/test/resources/openapi/spec_with_mandatory_array_query_parameter.yaml").toFeature()
        var queryParameterCount = 0
        contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                if(request.queryParams.isNotEmpty()) {
                    queryParameterCount = request.queryParams.paramPairs.count { it.first == "brand_ids" }
                }
                return HttpResponse.ok("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }
        })
        assertThat(queryParameterCount).isGreaterThan(1)
    }
}