package io.specmatic.stub.stateful

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.*
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.*
import io.specmatic.stub.ContractStub
import io.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

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

        assertThat(response.status).isEqualTo(201)
        val responseBody = response.body as JSONObjectValue

        resourceId = responseBody.getStringValue("id").orEmpty()

        assertThat(responseBody.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
        assertThat(responseBody.getStringValue("price")).isEqualTo("19.99")
        assertThat(responseBody.getStringValue("inStock")).isEqualTo("true")
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

        val responseObjectFromResponseBody = (response.body as JSONArrayValue).list.first() as JSONObjectValue

        assertThat(responseObjectFromResponseBody.getStringValue("name")).isEqualTo("Product A")
        assertThat(responseObjectFromResponseBody.getStringValue("description")).isEqualTo("A detailed description of Product A.")
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
    }

    @Test
    @Order(4)
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
    }

    @Test
    @Order(5)
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
    }

    private fun JSONObjectValue.getStringValue(key: String): String? {
        return this.jsonObject[key]?.toStringLiteral()
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
