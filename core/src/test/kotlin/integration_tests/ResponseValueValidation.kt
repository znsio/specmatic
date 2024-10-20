package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.pattern.parsedJSONArray
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResponseValueValidation {
    @Test
    fun `pass if response values from GET operation on entity match create entity operation`() {
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

                    return HttpResponse(201, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
                }

                return HttpResponse(200, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `fail if response values from GET operation on entity do not match create entity operation`() {
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

                    return HttpResponse(201, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
                }

                return HttpResponse(200, body = parsedJSONObject("""{"id": "2000", "name": "product name", "description": "product description", price: 10.0}"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isFalse()

        assertThat(results.report()).contains("Not all request values were returned in the response")

        println(results.report())
    }

    @Test
    fun `pass if response values from GET operation on entity collection match create entity operation`() {
        val basePath = "src/test/resources/openapi/workflow_with_response_value_array_validation"

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

                    return HttpResponse(201, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
                }

                return HttpResponse(200, body = parsedJSONArray("""[ {"id": "1000", "name": "product name", "description": "product description", price: 10.0} ]"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `fail if response values from GET operation on entity collection do not match create entity operation`() {
        val basePath = "src/test/resources/openapi/workflow_with_response_value_array_validation"

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

                    return HttpResponse(201, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
                }

                return HttpResponse(200, body = parsedJSONArray("""[ {"id": "1000", "name": "another product name", "description": "product description", price: 10.0} ]"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isFalse()

        assertThat(results.report()).contains("Not all request values were returned in the response")

        println(results.report())
    }

    @Test
    fun `pass if response value from GET operation on entity collection has at least one object with the right id`() {
        val basePath = "src/test/resources/openapi/workflow_with_response_value_array_validation"

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

                    return HttpResponse(201, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
                }

                val jsonObjectWithWrongID = parsedJSONObject("""{"id": "2000", "name": "product name", "description": "product description", price: 10.0}""")
                val jsonObjectWithCorrectID = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}""")
                val jsonArray = JSONArrayValue(listOf(jsonObjectWithCorrectID, jsonObjectWithWrongID))
                return HttpResponse(200, body = jsonArray)
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `pass if response value from GET operation on entity collection has no object with the right id`() {
        val basePath = "src/test/resources/openapi/workflow_with_response_value_array_validation"

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

                    return HttpResponse(201, body = parsedJSONObject("""{"id": "1000", "name": "product name", "description": "product description", price: 10.0}"""))
                }

                val jsonObjectWithWrongID = parsedJSONObject("""{"id": "2000", "name": "product name", "description": "product description", price: 10.0}""")
                val jsonObjectWithAnotherWrongID = parsedJSONObject("""{"id": "3000", "name": "product name", "description": "product description", price: 10.0}""")
                val jsonArray = JSONArrayValue(listOf(jsonObjectWithWrongID, jsonObjectWithAnotherWrongID))
                return HttpResponse(200, body = jsonArray)
            }
        })

        assertThat(results.report()).withFailMessage(results.report()).contains("""None of the objects returned had a property "id" with the value "1000"""")
        assertThat(results.success()).withFailMessage(results.report()).isFalse()
    }
}
