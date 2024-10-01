package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.pattern.parsedJSON
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.stub.HttpStub
import io.specmatic.stub.createStubFromContracts
import io.specmatic.stub.httpRequestLog
import io.specmatic.stub.httpResponseLog
import io.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DictionaryTest {
    @Test
    fun `should generate test values based on a dictionary found by convention in the same directory`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary/spec.yaml")
            .toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val jsonPayload = request.body as JSONObjectValue

                assertThat(jsonPayload.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo("input123")

                return HttpResponse.ok(parsedJSONObject("""{"data": "success"}"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `stubbed responses for request with no matching example should return dictionary values if available`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary/spec.yaml")
            .toFeature()

        HttpStub(feature).use { stub ->
            val response = stub.client.execute(HttpRequest("POST", "/data", body = parsedJSON("""{"name": "data"}""")))

            val jsonResponsePayload = response.body as JSONObjectValue

            assertThat(response.status).isEqualTo(200)

            assertThat(jsonResponsePayload.findFirstChildByPath("data")?.toStringLiteral()).isEqualTo("output123")
        }
    }

    @Test
    fun `tests should use dictionary to generate query params`() {
        val queryParamValueFromDictionary = "input123"

        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_and_query_params/spec.yaml")
            .toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.queryParams.asMap()["name"]).isEqualTo(queryParamValueFromDictionary)
                return HttpResponse.ok(parsedJSONObject("""{"data": "success"}""")).also {
                    println(httpRequestLog(request))
                    println(httpResponseLog(it))
                }
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `tests should use dictionary to generate request headers`() {
        val requestHeaderValueFromDictionary = "abc123"

        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_and_request_headers/spec.yaml")
            .toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.headers["X-LoginID"]).isEqualTo(requestHeaderValueFromDictionary)
                return HttpResponse.ok(parsedJSONObject("""{"data": "success"}""")).also {
                    println(httpRequestLog(request))
                    println(httpResponseLog(it))
                }
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `stub should return dictionary value if available for response header instead of random data`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_and_response_headers/spec.yaml")
            .toFeature()

        HttpStub(feature).use { stub ->
            val response = stub.client.execute(HttpRequest("POST", "/data", body = parsedJSONObject("""{"name": "data"}""")))
            assertThat(response.status).isEqualTo(200)
            assertThat(response.headers["X-Trace-ID"]).isEqualTo("trace123")
        }
    }

    @Test
    fun `stub should return dictionary value at the second level in a payload`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_and_multilevel_response/spec.yaml")
            .toFeature()

        HttpStub(feature).use { stub ->
            val request = HttpRequest("GET", "/person")
            println(request.toLogString())

            val response = stub.client.execute(request)

            println(response.toLogString())
            assertThat(response.status).isEqualTo(200)

            val json = response.body as JSONObjectValue
            assertThat(json.findFirstChildByPath("name.salutation")?.toStringLiteral()).isEqualTo("Ms")
            assertThat(json.findFirstChildByPath("name.full_name")?.toStringLiteral()).isEqualTo("Lena Schwartz")
        }
    }

    @Test
    fun `stub should return dictionary value at the second level in a schema`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_and_multilevel_schema/spec.yaml")
            .toFeature()

        HttpStub(feature).use { stub ->
            val request = HttpRequest("GET", "/person")

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            val json = response.body as JSONObjectValue

            assertThat(json.findFirstChildByPath("name.salutation")?.toStringLiteral()).isEqualTo("Ms")
            assertThat(json.findFirstChildByPath("name.details.first_name")?.toStringLiteral()).isEqualTo("Leanna")
            assertThat(json.findFirstChildByPath("name.details.last_name")?.toStringLiteral()).isEqualTo("Schwartz")
        }
    }

    @Test
    fun `stub should leverage dictionary object value at the second level in a schema`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_with_multilevel_schema_and_dictionary_object_value/spec.yaml")
            .toFeature()

        HttpStub(feature).use { stub ->
            val request = HttpRequest("GET", "/person")

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            val json = response.body as JSONObjectValue

            assertThat(json.findFirstChildByPath("name.salutation")?.toStringLiteral()).isEqualTo("Ms")
            assertThat(json.findFirstChildByPath("name.details.first_name")?.toStringLiteral()).isEqualTo("Leanna")
            assertThat(json.findFirstChildByPath("name.details.last_name")?.toStringLiteral()).isEqualTo("Schwartz")
        }
    }

    @Test
    fun `stub should leverage dictionary array scalar value at the second level in a schema`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_with_multilevel_schema_and_dictionary_array_value/spec.yaml")
            .toFeature()

        HttpStub(feature).use { stub ->
            val request = HttpRequest("GET", "/person")

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            val json = response.body as JSONObjectValue

            assertThat(json.findFirstChildByPath("details.addresses.[0]")?.toStringLiteral()).isEqualTo("22B Baker Street")
            assertThat(json.findFirstChildByPath("details.addresses.[1]")?.toStringLiteral()).isEqualTo("10A Horowitz Street")
        }
    }

    @Test
    fun `stub should leverage dictionary array object value at the second level in a schema`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_with_multilevel_schema_and_dictionary_array_objects/spec.yaml")
            .toFeature()

        HttpStub(feature).use { stub ->
            val request = HttpRequest("GET", "/person")

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            val json = response.body as JSONObjectValue

            val addresses = json.findFirstChildByPath("details.addresses") as JSONArrayValue

            assertThat(addresses.list).allSatisfy {
                val jsonAddressObject = it as JSONObjectValue
                assertThat(jsonAddressObject.jsonObject["street"]?.toStringLiteral()).isEqualTo("22B Baker Street")
            }
        }
    }

    @Test
    fun `stub should leverage dictionary array object value at the second level in a schema in an example`() {
        createStubFromContracts(
            listOf("src/test/resources/openapi/spec_with_dictionary_with_multilevel_schema_and_dictionary_array_objects_with_example/spec.yaml"),
            listOf("src/test/resources/openapi/spec_with_dictionary_with_multilevel_schema_and_dictionary_array_objects_with_example/spec_examples"),
            timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/person")

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            val json = response.body as JSONObjectValue

            val addresses = json.findFirstChildByPath("details.addresses") as JSONArrayValue

            assertThat(addresses.list).allSatisfy {
                val jsonAddressObject = it as JSONObjectValue
                assertThat(jsonAddressObject.jsonObject["street"]?.toStringLiteral()).isEqualTo("22B Baker Street")
            }
        }
    }

    @Test
    fun `stub should leverage dictionary object value at the second level given oneOf in a schema in an example`() {
        createStubFromContracts(
            listOf("src/test/resources/openapi/spec_with_dictionary_with_multilevel_schema_and_dictionary_array_objects_with_example/spec.yaml"),
            listOf("src/test/resources/openapi/spec_with_dictionary_with_multilevel_schema_and_dictionary_array_objects_with_example/spec_examples"),
            timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/person")

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            val json = response.body as JSONObjectValue

            val addresses = json.findFirstChildByPath("details.addresses") as JSONArrayValue

            assertThat(addresses.list).allSatisfy {
                val jsonAddressObject = it as JSONObjectValue
                assertThat(jsonAddressObject.jsonObject["street"]?.toStringLiteral()).isEqualTo("22B Baker Street")
            }
        }
    }

    @Test
    fun `generative tests with a dictionary work as usual`() {
        val testCountWithoutDictionary = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_with_dictionary_and_constraints/spec.yaml")
            .toFeature()
            .enableGenerativeTesting().let { feature ->
                feature.executeTests(object : TestExecutor {
                    override fun execute(request: HttpRequest): HttpResponse {
                        return HttpResponse.ok("success")
                    }
                })
            }.let { results ->
                results.testCount
            }

        val testCountWithDictionary = try {
            System.setProperty(SPECMATIC_STUB_DICTIONARY, "src/test/resources/openapi/spec_with_dictionary_and_constraints/dictionary.json")

            OpenApiSpecification
                .fromFile("src/test/resources/openapi/spec_with_dictionary_and_constraints/spec.yaml")
                .toFeature()
                .enableGenerativeTesting().let { feature ->
                    feature.executeTests(object : TestExecutor {
                        override fun execute(request: HttpRequest): HttpResponse {
                            return HttpResponse.ok("success")
                        }
                    })
                }.let { results ->
                    results.testCount
                }
        } finally {
            System.clearProperty(SPECMATIC_STUB_DICTIONARY)
        }

        assertThat(testCountWithDictionary).isEqualTo(testCountWithoutDictionary)
    }
}