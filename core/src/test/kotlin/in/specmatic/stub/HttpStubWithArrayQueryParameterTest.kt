package `in`.specmatic.stub

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.QueryParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HttpStubWithArrayQueryParameterTest {

    @Test
    fun `test stub with mandatory array query parameter`() {
        val contract = OpenApiSpecification.fromFile("src/test/resources/openapi/spec_with_mandatory_array_query_parameter.yaml").toFeature()

        HttpStub(contract).use { stub ->
            val queryParameters = QueryParameters(paramPairs = listOf("brand_ids" to "1", "brand_ids" to "2", "brand_ids" to "3"))
            val response = stub.client.execute(HttpRequest("GET", "/products", queryParams = queryParameters) )

            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toStringLiteral()).isNotEmpty
        }
    }

    @Test
    fun `test stub with mandatory array query parameter with dynamic expectation`() {
        val contract = OpenApiSpecification.fromFile("src/test/resources/openapi/spec_with_mandatory_array_query_parameter.yaml").toFeature()

        HttpStub(contract).use { stub ->
            stub.setExpectation("""
                {
                    "http-request": {
                        "method": "GET",
                        "path": "/products",
                         "query": {
                            "brand_ids": [1,2,3]
                        }

                    },
                    "http-response": {
                        "status": 200,
                        "body": "product list"
                    }
                }
            """.trimIndent())

            val queryParameters = QueryParameters(paramPairs = listOf("brand_ids" to "1", "brand_ids" to "2", "brand_ids" to "3"))
            val response = stub.client.execute(HttpRequest("GET", "/products", queryParams = queryParameters) )

            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toString()).isEqualTo("product list")
        }
    }

    @Test
    fun `test stub with mandatory array query parameter with externalized json expectation`() {
        createStubFromContracts(listOf("src/test/resources/openapi/spec_with_mandatory_array_query_parameter.yaml")).use { stub ->
            val queryParameters = QueryParameters(paramPairs = listOf("brand_ids" to "1", "brand_ids" to "2", "brand_ids" to "3"))
            val response = stub.client.execute(HttpRequest("GET", "/products", queryParams = queryParameters) )

            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toString()).isEqualTo("product list from externalized json")
        }
    }

    @Test
    fun `test does not match stub for array query parameter when request contains a query parameter value which is not defined in the stub`() {
        val contract = OpenApiSpecification.fromFile("src/test/resources/openapi/spec_with_mandatory_array_query_parameter.yaml").toFeature()
        HttpStub(contract).use { stub ->
            stub.setExpectation("""
                {
                    "http-request": {
                        "method": "GET",
                        "path": "/products",
                         "query": {
                            "brand_ids": [1,2,3]
                        }

                    },
                    "http-response": {
                        "status": 200,
                        "body": "product list"
                    }
                }
            """.trimIndent())

            val queryParameters = QueryParameters(paramPairs = listOf("brand_ids" to "1", "brand_ids" to "2", "brand_ids" to "4"))
            val response = stub.client.execute(HttpRequest("GET", "/products", queryParams = queryParameters) )

            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toString()).isNotEqualTo("product list")
            assertThat(response.headers["X-Specmatic-Type"]).isEqualTo("random")
        }
    }

    @Test
    fun `test does not match stub for array query parameter when request does not contain all the query parameter values defined in the stub`() {
        createStubFromContracts(listOf("src/test/resources/openapi/spec_with_mandatory_array_query_parameter.yaml")).use { stub ->
            stub.setExpectation("""
                {
                    "http-request": {
                        "method": "GET",
                        "path": "/products",
                         "query": {
                            "brand_ids": [1,2,3]
                        }

                    },
                    "http-response": {
                        "status": 200,
                        "body": "product list"
                    }
                }
            """.trimIndent())

            val queryParameters = QueryParameters(paramPairs = listOf("brand_ids" to "1", "brand_ids" to "2"))
            val response = stub.client.execute(HttpRequest("GET", "/products", queryParams = queryParameters) )

            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toString()).isNotEqualTo("product list")
            assertThat(response.headers["X-Specmatic-Type"]).isEqualTo("random")
        }
    }
}