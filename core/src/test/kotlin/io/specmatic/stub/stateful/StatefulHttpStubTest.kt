package io.specmatic.stub.stateful

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.*
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.core.value.*
import io.specmatic.stub.ContractStub
import io.specmatic.stub.loadContractStubsFromImplicitPaths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors


@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StatefulHttpStubMonitorPatternForPOSTRequestTest {
    companion object {
        private lateinit var httpStub: ContractStub
        private const val SPEC_DIR_PATH = "src/test/resources/stateful-stub-monitor-pattern"
        private var resourceId = ""
        private var monitorLink = ""

        @JvmStatic
        @BeforeAll
        fun setup() {
            val specPath = "$SPEC_DIR_PATH/monitor-pattern-spec.yaml"
            val scenarioStubs = loadContractStubsFromImplicitPaths(
                contractPathDataList = listOf(ContractPathData("", specPath)),
                specmaticConfig = loadSpecmaticConfig("$SPEC_DIR_PATH/specmatic.yaml")
            ).flatMap { it.second }

            assertThat(scenarioStubs).withFailMessage(
                "Failed while loading the monitor substitution based example"
            ).isNotEmpty

            httpStub = StatefulHttpStub(
                specmaticConfigPath = "$SPEC_DIR_PATH/specmatic.yaml",
                features = listOf(
                    OpenApiSpecification.fromFile(specPath).toFeature()
                ),
                scenarioStubs = scenarioStubs
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            httpStub.close()
        }
    }

    @Test
    @Order(1)
    fun `should post a product and get 202`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "POST",
                path = "/product",
                headers = mapOf(
                    SPECMATIC_RESPONSE_CODE_HEADER to "202"
                ),
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product A",
                      "price": 19.99
                    }
                    """.trimIndent()
                )
            )
        )

        assertThat(response.status).isEqualTo(202)
        assertThat(response.headers).containsKeys("Link")

        monitorLink = response.headers.getValue("Link")
        resourceId = monitorLink.split("/")[2].substringBefore(">")
    }

    @Test
    @Order(2)
    fun `should get the current status of previous post product request from monitor endpoint`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/monitor/$resourceId"
            )
        )

        assertThat(response.status).isEqualTo(200)

        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.findFirstChildByPath("response.status")?.toStringLiteral()).isEqualTo("201")

        val requestBodyFromResponse = (responseBody.findFirstChildByPath("request.body") as JSONObjectValue)
        assertThat(requestBodyFromResponse.getStringValue("name")).isEqualTo("Product A")
        assertThat(requestBodyFromResponse.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseBody.findFirstChildByPath("request.method")?.toStringLiteral()).isEqualTo("POST")

        val responseBodyFromResponse = responseBody.findFirstChildByPath("response.body") as JSONObjectValue
        assertThat(responseBodyFromResponse.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseBodyFromResponse.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseBodyFromResponse.getStringValue("id")).isNotNull()
    }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StatefulHttpStubMonitorPatternForPATCHRequestTest {
    companion object {
        private lateinit var httpStub: ContractStub
        private const val SPEC_DIR_PATH = "src/test/resources/stateful-stub-monitor-pattern"
        private var resourceId = ""
        private var monitorLink = ""

        @JvmStatic
        @BeforeAll
        fun setup() {
            val specPath = "$SPEC_DIR_PATH/monitor-pattern-spec.yaml"
            val scenarioStubs = loadContractStubsFromImplicitPaths(
                contractPathDataList = listOf(ContractPathData("", specPath)),
                specmaticConfig = loadSpecmaticConfig("$SPEC_DIR_PATH/specmatic.yaml")
            ).flatMap { it.second }

            assertThat(scenarioStubs).withFailMessage(
                "Failed while loading the monitor substitution based example"
            ).isNotEmpty

            httpStub = StatefulHttpStub(
                specmaticConfigPath = "$SPEC_DIR_PATH/specmatic.yaml",
                features = listOf(
                    OpenApiSpecification.fromFile(specPath).toFeature()
                ),
                scenarioStubs = scenarioStubs
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            httpStub.close()
        }
    }

    @Test
    @Order(1)
    fun `should post a product and get 202`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "POST",
                path = "/product",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product A",
                      "price": 19.99
                    }
                    """.trimIndent()
                )
            )
        )

        assertThat(response.status).isEqualTo(201)

        resourceId = (response.body as JSONObjectValue).getStringValue("id").orEmpty()
    }

    @Test
    @Order(2)
    fun `should patch a product and get 202`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "PATCH",
                path = "/product/$resourceId",
                headers = mapOf(
                    SPECMATIC_RESPONSE_CODE_HEADER to "202"
                ),
                body = parsedJSONObject(
                    """
                    {
                      "price": 40
                    }
                    """.trimIndent()
                )
            )
        )

        assertThat(response.status).isEqualTo(202)
        assertThat(response.headers).containsKeys("Link")

        monitorLink = response.headers.getValue("Link")
        resourceId = monitorLink.split("/")[2].substringBefore(">")
    }

    @Test
    @Order(3)
    fun `should get the current status of previous patch product request from monitor endpoint`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/monitor/$resourceId"
            )
        )

        assertThat(response.status).isEqualTo(200)

        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.findFirstChildByPath("response.status")?.toStringLiteral()).isEqualTo("200")

        val requestBodyFromResponse = (responseBody.findFirstChildByPath("request.body") as JSONObjectValue)
        assertThat(requestBodyFromResponse.getStringValue("price")).isEqualTo("40")
        assertThat(responseBody.findFirstChildByPath("request.method")?.toStringLiteral()).isEqualTo("PATCH")

        val responseBodyFromResponse = responseBody.findFirstChildByPath("response.body") as JSONObjectValue
        assertThat(responseBodyFromResponse.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseBodyFromResponse.getStringValue("price")).isEqualTo("40")
        assertThat(responseBodyFromResponse.getStringValue("id")).isNotNull()
    }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StatefulHttpStubTest {
    companion object {
        private lateinit var httpStub: ContractStub
        private const val SPEC_DIR_PATH = "src/test/resources/openapi/spec_with_strictly_restful_apis"
        private var resourceId = ""

        @JvmStatic
        @BeforeAll
        fun setup() {
            httpStub = StatefulHttpStub(
                specmaticConfigPath = "$SPEC_DIR_PATH/specmatic.yaml",
                features = listOf(
                    OpenApiSpecification.fromFile(
                        "$SPEC_DIR_PATH/spec_with_strictly_restful_apis.yaml"
                    ).toFeature()
                )
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            httpStub.close()
        }
    }

    @Test
    @Order(1)
    fun `should post a product`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "POST",
                path = "/products",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product A",
                      "description": "A detailed description of Product A.",
                      "price": 19.99,
                      "inStock": true
                    }
                    """.trimIndent()
                )
            )
        )

        val anotherResponse = httpStub.client.execute(
            HttpRequest(
                method = "POST",
                path = "/products",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product B",
                      "description": "A detailed description of Product B.",
                      "price": 100,
                      "inStock": false 
                    }
                    """.trimIndent()
                )
            )
        )

        assertThat(response.status).isEqualTo(201)
        assertThat(anotherResponse.status).isEqualTo(201)
        val responseBody = response.body as JSONObjectValue

        resourceId = responseBody.getStringValue("id").orEmpty()

        assertThat(responseBody.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
        assertThat(responseBody.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseBody.getStringValue("inStock")).isEqualTo("true")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(2)
    fun `should get the list of products`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products"
            )
        )

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(JSONArrayValue::class.java)
        val responseBody = response.body as JSONArrayValue

        assertThat(responseBody.list.size).isEqualTo(2)

        val responseObjectFromResponseBody = responseBody.list.first() as JSONObjectValue

        assertThat(responseObjectFromResponseBody.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseObjectFromResponseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
        assertThat(responseObjectFromResponseBody.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseObjectFromResponseBody.getStringValue("inStock")).isEqualTo("true")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(3)
    fun `should get the list of products filtered based on name and price passed in query params`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products?name=Product%20A&price=19.99"
            )
        )

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(JSONArrayValue::class.java)
        val responseBody = response.body as JSONArrayValue

        assertThat(responseBody.list.size).isEqualTo(1)

        val responseObjectFromResponseBody = responseBody.list.first() as JSONObjectValue

        assertThat(responseObjectFromResponseBody.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseObjectFromResponseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
        assertThat(responseObjectFromResponseBody.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseObjectFromResponseBody.getStringValue("inStock")).isEqualTo("true")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(4)
    fun `should update an existing product with patch except for the non-patchable 'description' key`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "PATCH",
                path = "/products/$resourceId",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product B",
                      "price": 100,
                      "description": "random description"
                    }
                    """.trimIndent()
                )
            )
        )

        assertThat(response.status).isEqualTo(200)
        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.getStringValue("id")).isEqualTo(resourceId)
        assertThat(responseBody.getStringValue("name")).isEqualTo("Product B")
        assertThat(responseBody.getStringValue("price")).isEqualTo("100")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
        assertThat(responseBody.getStringValue("inStock")).isEqualTo("true")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(5)
    fun `should get the updated product`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products/$resourceId"
            )
        )

        assertThat(response.status).isEqualTo(200)
        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.getStringValue("id")).isEqualTo(resourceId)
        assertThat(responseBody.getStringValue("name")).isEqualTo("Product B")
        assertThat(responseBody.getStringValue("price")).isEqualTo("100")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
        assertThat(responseBody.getStringValue("inStock")).isEqualTo("true")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(6)
    fun `should delete a product`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "DELETE",
                path = "/products/$resourceId"
            )
        )

        assertThat(response.status).isEqualTo(204)

        val getResponse = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products/$resourceId"
            )
        )

        assertThat(getResponse.status).isEqualTo(404)

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(7)
    fun `should post a product even though the request contains unknown keys`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "POST",
                path = "/products",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product A",
                      "description": "A detailed description of Product A.",
                      "price": 19.99,
                      "inStock": true,
                      "random": "random",
                      "other": 10
                    }
                    """.trimIndent()
                )
            )
        )

        assertThat(response.status).isEqualTo(201)
        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
        assertThat(responseBody.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseBody.getStringValue("inStock")).isEqualTo("true")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(8)
    fun `should get a 400 response in a structured manner for an invalid post request`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "POST",
                path = "/products",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product A",
                      "description": "A detailed description of Product A.",
                      "price": 19.99,
                      "inStock": "true"
                    }
                    """.trimIndent()
                )
            )
        )

        assertThat(response.status).isEqualTo(400)
        val responseBody = response.body as JSONObjectValue
        val error = responseBody.getStringValue("error")
        assertThat(error).contains(">> REQUEST.BODY.inStock")
        assertThat(error).contains("Contract expected boolean but request contained \"true\"")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Order(9)
    @Test
    fun `should get a 400 response as a string for an invalid get request where 400 schema is not defined for the same in the spec`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products/invalid-id"
            )
        )

        assertThat(response.status).isEqualTo(400)
        val responseBody = (response.body as StringValue).toStringLiteral()
        assertThat(responseBody).contains(">> REQUEST.PATH.id")
        assertThat(responseBody).contains("Contract expected number but request contained \"invalid-id\"")
        assertThat(responseBody).contains("WARNING: The response is in string format since no schema found in the specification for 400 response")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(10)
    fun `should get a 404 response in a structured manner for a get request where the entry with requested id is not present in the cache`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products/0"
            )
        )

        assertThat(response.status).isEqualTo(404)
        val responseBody = response.body as JSONObjectValue
        val message = responseBody.getStringValue("message")
        assertThat(message).isEqualTo("Resource with resourceId '0' not found")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }

    @Test
    @Order(11)
    fun `should get a 404 response as a string for a delete request with missing id where 404 schema is not defined for the same in the spec`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "DELETE",
                path = "/products/0"
            )
        )

        assertThat(response.status).isEqualTo(404)
        val responseBody = response.body as StringValue
        assertThat(responseBody.toStringLiteral()).contains("Resource with resourceId '0' not found")
        assertThat(responseBody.toStringLiteral()).contains("WARNING: The response is in string format since no schema found in the specification for 404 response")

        assertThat(response.headers.containsKey("X-Specmatic-Type")).isFalse()
    }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StatefulHttpStubWithAttributeSelectionTest {
    companion object {
        private lateinit var httpStub: ContractStub
        private const val SPEC_DIR_PATH = "src/test/resources/openapi/spec_with_strictly_restful_apis"
        private var resourceId = ""

        @JvmStatic
        @BeforeAll
        fun setup() {
            val feature = OpenApiSpecification.fromFile(
                "$SPEC_DIR_PATH/spec_with_strictly_restful_apis.yaml"
            ).toFeature()
            val specmaticConfig = loadSpecmaticConfig("$SPEC_DIR_PATH/specmatic.yaml")
            val scenarios = feature.scenarios.map {
                it.copy(attributeSelectionPattern = specmaticConfig.attributeSelectionPattern)
            }
            httpStub = StatefulHttpStub(
                specmaticConfigPath = "$SPEC_DIR_PATH/specmatic.yaml",
                features = listOf(feature.copy(scenarios = scenarios))
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            httpStub.close()
        }
    }

    @Test
    @Order(1)
    fun `should post a product`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "POST",
                path = "/products",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product A",
                      "description": "A detailed description of Product A.",
                      "price": 19.99,
                      "inStock": true
                    }
                    """.trimIndent()
                ),
                queryParams = QueryParameters(
                    mapOf(
                        "columns" to "name,description"
                    )
                )
            )
        )

        assertThat(response.status).isEqualTo(201)
        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.jsonObject.keys).containsExactlyInAnyOrder("id", "name", "description")

        resourceId = responseBody.getStringValue("id").orEmpty()

        assertThat(responseBody.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
    }

    @Test
    @Order(2)
    fun `should get the list of products`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products",
                queryParams = QueryParameters(
                    mapOf(
                        "columns" to "price,inStock"
                    )
                )
            )
        )

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(JSONArrayValue::class.java)

        val responseObjectFromResponseBody = (response.body as JSONArrayValue).list.first() as JSONObjectValue

        assertThat(responseObjectFromResponseBody.jsonObject.keys).containsExactlyInAnyOrder("id", "price", "inStock")

        assertThat(responseObjectFromResponseBody.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseObjectFromResponseBody.getStringValue("inStock")).isEqualTo("true")
    }

    @Test
    @Order(3)
    fun `should update an existing product with patch`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "PATCH",
                path = "/products/$resourceId",
                body = parsedJSONObject(
                    """
                    {
                      "name": "Product B",
                      "price": 100
                    }
                    """.trimIndent()
                ),
                queryParams = QueryParameters(
                    mapOf(
                        "columns" to "name,description"
                    )
                )
            )
        )

        assertThat(response.status).isEqualTo(200)
        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.jsonObject.keys).containsExactlyInAnyOrder("id", "name", "description")
        assertThat(responseBody.getStringValue("id")).isEqualTo(resourceId)
        assertThat(responseBody.getStringValue("name")).isEqualTo("Product B")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
    }

    @Test
    @Order(4)
    fun `should get the updated product`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products/$resourceId",
                queryParams = QueryParameters(
                    mapOf(
                        "columns" to "name,description,price"
                    )
                )
            )
        )

        assertThat(response.status).isEqualTo(200)
        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.jsonObject.keys).containsExactlyInAnyOrder("id", "name", "price", "description")
        assertThat(responseBody.getStringValue("id")).isEqualTo(resourceId)
        assertThat(responseBody.getStringValue("name")).isEqualTo("Product B")
        assertThat(responseBody.getStringValue("price")).isEqualTo("100")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
    }

    private fun JSONObjectValue.getStringValue(key: String): String? {
        return this.jsonObject[key]?.toStringLiteral()
    }
}

class StatefulHttpStubSeedDataFromExamplesTest {
    companion object {
        private lateinit var httpStub: ContractStub
        private const val SPEC_DIR_PATH = "src/test/resources/openapi/spec_with_strictly_restful_apis"

        @JvmStatic
        @BeforeAll
        fun setup() {
            val specPath = "$SPEC_DIR_PATH/spec_with_strictly_restful_apis.yaml"

            val scenarioStubs = loadContractStubsFromImplicitPaths(
                contractPathDataList = listOf(ContractPathData("", specPath)),
                specmaticConfig = loadSpecmaticConfig("$SPEC_DIR_PATH/specmatic.yaml")
            ).flatMap { it.second }

            httpStub = StatefulHttpStub(
                specmaticConfigPath = "$SPEC_DIR_PATH/specmatic.yaml",
                features = listOf(
                    OpenApiSpecification.fromFile(specPath).toFeature()
                ),
                scenarioStubs = scenarioStubs
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            httpStub.close()
        }
    }

    @Test
    fun `should get the list of products from seed data loaded from examples`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products"
            )
        )

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(JSONArrayValue::class.java)

        val responseBody = (response.body as JSONArrayValue)
        assertThat(responseBody.list.size).isEqualTo(4)

        val responseObjectFromResponseBody = (response.body as JSONArrayValue)
            .list.filterIsInstance<JSONObjectValue>().first { it.getStringValue("id") == "300" }

        assertThat(responseObjectFromResponseBody.getStringValue("id")).isEqualTo("300")
        assertThat(responseObjectFromResponseBody.getStringValue("name")).isEqualTo("iPhone 16")
        assertThat(responseObjectFromResponseBody.getStringValue("description")).isEqualTo("New iPhone 16")
        assertThat(responseObjectFromResponseBody.getStringValue("price")).isEqualTo("942")
        assertThat(responseObjectFromResponseBody.getStringValue("inStock")).isEqualTo("true")
    }


    @Test
    fun `should get the product from seed data loaded from examples`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products/300"
            )
        )

        assertThat(response.status).isEqualTo(200)
        val responseBody = response.body as JSONObjectValue

        assertThat(responseBody.getStringValue("id")).isEqualTo("300")
        assertThat(responseBody.getStringValue("name")).isEqualTo("iPhone 16")
        assertThat(responseBody.getStringValue("description")).isEqualTo("New iPhone 16")
        assertThat(responseBody.getStringValue("price")).isEqualTo("942")
        assertThat(responseBody.getStringValue("inStock")).isEqualTo("true")
    }

    @Test
    fun `should not load the xiaomi product from the seed data as it has the same id (300) as that of iphone example`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products"
            )
        )

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(JSONArrayValue::class.java)

        val responseBody = (response.body as JSONArrayValue)
        assertThat(responseBody.list.size).isEqualTo(4)

        val responseObjectsFromResponseBody = (response.body as JSONArrayValue)
            .list.filterIsInstance<JSONObjectValue>().filter { it.getStringValue("id") == "300" }

        assertThat(responseObjectsFromResponseBody.size).isEqualTo(1)

        val responseObjectFromResponseBody = responseObjectsFromResponseBody.first()
        assertThat(responseObjectFromResponseBody.getStringValue("id")).isEqualTo("300")
        assertThat(responseObjectFromResponseBody.getStringValue("name")).isEqualTo("iPhone 16")
    }

    @Test
    fun `should not load the example which has products with ids 500 and 600 as it involves attribute selection`() {
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products"
            )
        )

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(JSONArrayValue::class.java)

        val responseBody = (response.body as JSONArrayValue)
        assertThat(responseBody.list.size).isEqualTo(4)

        val productsWithIds500And600 = (response.body as JSONArrayValue)
            .list.filterIsInstance<JSONObjectValue>().filter {
                it.getStringValue("id") == "500" ||
                        it.getStringValue("id") == "600"
            }

        assertThat(productsWithIds500And600).isEmpty()
    }
}

class StatefulHttpStubConcurrencyTest {
    companion object {
        private lateinit var httpStub: ContractStub
        private const val SPEC_DIR_PATH = "src/test/resources/openapi/spec_with_strictly_restful_apis"
        private var resourceId = ""

        @JvmStatic
        @BeforeAll
        fun setup() {
            httpStub = StatefulHttpStub(
                specmaticConfigPath = "$SPEC_DIR_PATH/specmatic.yaml",
                features = listOf(
                    OpenApiSpecification.fromFile(
                        "$SPEC_DIR_PATH/spec_with_strictly_restful_apis.yaml"
                    ).toFeature()
                )
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            httpStub.close()
        }
    }

    @Test
    fun `should handle concurrent additions and updates without corruption`() {
        val numberOfThreads = 10
        val executor = Executors.newFixedThreadPool(numberOfThreads)
        val latch = CountDownLatch(numberOfThreads)

        repeat(numberOfThreads) { threadIndex ->
            executor.submit {
                try {
                    val path = "/products"

                    httpStub.client.execute(
                        HttpRequest(
                            method = "POST",
                            path = path,
                            body = parsedJSONObject(
                                """
                                {
                                  "name": "Product $threadIndex",
                                  "price": ${threadIndex * 10}
                                }
                                """.trimIndent()
                            )
                        )
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Verify all products were added
        val response = httpStub.client.execute(
            HttpRequest(
                method = "GET",
                path = "/products"
            )
        )

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(JSONArrayValue::class.java)

        val products = (response.body as JSONArrayValue).list
        assertThat(products.size).isEqualTo(numberOfThreads)
        products.sortedBy { (it as JSONObjectValue).getStringValue("name") }.forEachIndexed { index, product ->
            val productObject = product as JSONObjectValue
            assertThat(productObject.getStringValue("name")).isEqualTo("Product $index")
            assertThat(productObject.getStringValue("price")).isEqualTo("${index * 10}")
        }
    }
}

private fun JSONObjectValue.getStringValue(key: String): String? {
    return this.jsonObject[key]?.toStringLiteral()
}
