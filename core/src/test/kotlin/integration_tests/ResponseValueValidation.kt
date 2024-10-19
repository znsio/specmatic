package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResponseValueValidation {
    @Test
    fun `validate response values`() {
        val basePath = "src/test/resources/openapi/workflow_with_response_value_validation"

        val feature = OpenApiSpecification.fromFileAndConfig(
            "$basePath/products.yaml",
            "$basePath/specmatic.yaml"
        ).toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                if(request.method == "POST") {
                    val jsonRequestBody = request.body as JSONObjectValue

                    assertThat(
                        jsonRequestBody.findFirstChildByPath("name")?.toStringLiteral()
                    ).isEqualTo("Sample Product")
                }

                val status = when(request.method) {
                    "POST" -> 201
                    else -> 200
                }

                return HttpResponse(status, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isFalse()

//        assertThat(results.report()).contains("Values sent in the request to POST /products were not returned")
        assertThat(results.report()).contains("Not all request values were returned in the response")

        println(results.report())
    }
}
