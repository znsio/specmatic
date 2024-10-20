package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.pattern.parsedJSONArray
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
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
        assertThat(results.report()).withFailMessage(results.report()).contains("""Expected "1000" as per the entity created but was "2000"""")
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

        assertThat(results.report()).contains("""Expected "product name" as per the entity created but was "another product name"""")

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

    @Test
    fun `run retrieve test after PATCH operation on entity`() {
        val basePath = "src/test/resources/openapi/workflow_with_response_value_validation_for_patch"

        val feature = OpenApiSpecification.fromFileAndConfig(
            "$basePath/products.yaml",
            "$basePath/specmatic.yaml"
        ).toFeature()

        val requestsSeen = mutableListOf<String>()

        val results = feature.executeTests(object : TestExecutor {
            var entity = parsedJSONObject("""{"id": "abc123", "name": "product name", "description": "product description", price: 10.0}""")

            override fun execute(request: HttpRequest): HttpResponse {
                requestsSeen.add("${request.method} ${request.path!!}")

                val response = when (request.method) {
                    "POST" -> {
                        val jsonRequestBody = request.body as JSONObjectValue

                        assertThat(
                            jsonRequestBody.findFirstChildByPath("name")?.toStringLiteral()
                        ).isEqualTo("Sample Product")

                        HttpResponse(201, body = entity)
                    }
                    "PATCH" -> {
                        val newEntityValue = request.body as JSONObjectValue
                        val entityMap = newEntityValue.jsonObject

                        val newProductName = "name" to StringValue("new product name")
                        val id = "id" to StringValue("abc123")

                        val newEntityMap = entityMap
                            .plus(newProductName)
                            .plus(id)

                        entity = JSONObjectValue(newEntityMap)
                        HttpResponse(200, body = entity)
                    }
                    else ->
                        HttpResponse(200, body = entity)
                }

                println(request.toLogString())
                println()
                println(response.toLogString())
                println()
                println()

                return response
            }
        })

        assertThat(requestsSeen).containsExactly("POST /products", "GET /products/abc123", "PATCH /products/abc123", "GET /products/abc123")
        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `retrieve test after PATCH operation on entity fails if the new value is not retrieved`() {
        val basePath = "src/test/resources/openapi/workflow_with_response_value_validation_for_patch"

        val feature = OpenApiSpecification.fromFileAndConfig(
            "$basePath/products.yaml",
            "$basePath/specmatic.yaml"
        ).toFeature()

        val requestsSeen = mutableListOf<String>()

        val results = feature.executeTests(object : TestExecutor {
            val entity = parsedJSONObject("""{"id": "abc123", "name": "product name", "description": "product description", price: 10.0}""")

            override fun execute(request: HttpRequest): HttpResponse {
                requestsSeen.add("${request.method} ${request.path!!}")

                val response = when (request.method) {
                    "POST" -> {
                        val jsonRequestBody = request.body as JSONObjectValue

                        assertThat(
                            jsonRequestBody.findFirstChildByPath("name")?.toStringLiteral()
                        ).isEqualTo("Sample Product")

                        HttpResponse(201, body = entity)
                    }
                    "PATCH" -> {
                        val newEntityValue = request.body as JSONObjectValue
                        val entityMap = newEntityValue.jsonObject

                        val newProductName = "name" to StringValue("new product name")
                        val id = "id" to StringValue("abc123")

                        val newEntityMap = entityMap
                            .plus(newProductName)
                            .plus(id)

                        HttpResponse(200, body = JSONObjectValue(newEntityMap))
                    }
                    else ->
                        HttpResponse(200, body = entity)
                }

                println(request.toLogString())
                println()
                println(response.toLogString())
                println()
                println()

                return response
            }
        })

        assertThat(requestsSeen).isEqualTo(listOf("POST /products", "GET /products/abc123", "PATCH /products/abc123", "GET /products/abc123"))
        assertThat(results.success()).withFailMessage(results.report()).isFalse()
    }
}
