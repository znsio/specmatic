package integration_tests

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSONArray
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OmittedParameterExamples {
    @Test
    fun `omitted optional query params should not be sent in any contract test`() {
        val productSpec = OpenApiSpecification.fromYAML("""
openapi: 3.0.3
info:
  title: Product Search Service
  description: API for searching product details
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product/search:
    get:
      summary: Search for product details
      parameters:
        - name: productName
          in: query
          description: Name of the product to search for
          required: false
          schema:
            type: string
        - name: productCategory
          in: query
          description: Category of the product to search for
          required: true
          schema:
            type: string
          examples:
            PRODUCT_SEARCH:
              value: "Electronics"
        - name: priceRange
          in: query
          description: Price range of the product to search for
          required: false
          schema:
            type: string
          examples:
            PRODUCT_SEARCH:
              value: "1000-2000"
      responses:
        200:
          description: Successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  ${"$"}ref: '#/components/schemas/Product'
              examples:
                PRODUCT_SEARCH:
                    value:
                      - id: 1
                        name: "Product 1"
                      - id: 2
                        name: "Product 2"
components:
  schemas:
    Product:
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
        """.trimIndent(), "").toFeature()

        val optionalQueryParam = "productName"

        val results = productSpec.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.queryParams.keys).doesNotContain(optionalQueryParam)
                return HttpResponse(200, body = parsedJSONArray("""[{"id": 1, "name": "Product 1"}, {"id": 2, "name": "Product 2"}]"""))
            }
        })

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isPositive()
    }

    @Test
    fun `mandatory query param without example should be generated`() {
        val productSpec = OpenApiSpecification.fromYAML("""
openapi: 3.0.3
info:
  title: Product Search Service
  description: API for searching product details
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product/search:
    get:
      summary: Search for product details
      parameters:
        - name: productCategory
          in: query
          description: Category of the product to search for
          required: true
          schema:
            type: string
        - name: priceRange
          in: query
          description: Price range of the product to search for
          required: false
          schema:
            type: string
          examples:
            PRODUCT_SEARCH:
              value: "1000-2000"
      responses:
        200:
          description: Successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  ${"$"}ref: '#/components/schemas/Product'
              examples:
                PRODUCT_SEARCH:
                    value:
                      - id: 1
                        name: "Product 1"
                      - id: 2
                        name: "Product 2"
components:
  schemas:
    Product:
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
        """.trimIndent(), "").toFeature()

        val optionalQueryParam = "productName"

        val results = productSpec.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.queryParams.keys).contains("productCategory")
                return HttpResponse(200, body = parsedJSONArray("""[{"id": 1, "name": "Product 1"}, {"id": 2, "name": "Product 2"}]"""))
            }
        })

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `mandatory header without example should be generated`() {
        val productSpec = OpenApiSpecification.fromYAML("""
openapi: 3.0.3
info:
  title: Product Search Service
  description: API for searching product details
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product/search:
    get:
      summary: Search for product details
      parameters:
        - name: productCategory
          in: header
          description: Category of the product to search for
          required: true
          schema:
            type: string
        - name: priceRange
          in: header
          description: Price range of the product to search for
          required: false
          schema:
            type: string
          examples:
            PRODUCT_SEARCH:
              value: "1000-2000"
      responses:
        200:
          description: Successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  ${"$"}ref: '#/components/schemas/Product'
              examples:
                PRODUCT_SEARCH:
                    value:
                      - id: 1
                        name: "Product 1"
                      - id: 2
                        name: "Product 2"
components:
  schemas:
    Product:
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
        """.trimIndent(), "").toFeature()

        val results = productSpec.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.headers.keys).contains("productCategory")
                return HttpResponse(200, body = parsedJSONArray("""[{"id": 1, "name": "Product 1"}, {"id": 2, "name": "Product 2"}]"""))
            }
        })

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `omitted optional query params should be sent when generative tests are switch on`() {
        val productSpec = OpenApiSpecification.fromYAML("""
openapi: 3.0.3
info:
  title: Product Search Service
  description: API for searching product details
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product/search:
    get:
      summary: Search for product details
      parameters:
        - name: productName
          in: query
          description: Name of the product to search for
          required: false
          schema:
            type: string
        - name: productCategory
          in: query
          description: Category of the product to search for
          required: true
          schema:
            type: string
          examples:
            PRODUCT_SEARCH:
              value: "Electronics"
        - name: priceRange
          in: query
          description: Price range of the product to search for
          required: false
          schema:
            type: string
          examples:
            PRODUCT_SEARCH:
              value: "1000-2000"
      responses:
        200:
          description: Successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  ${"$"}ref: '#/components/schemas/Product'
              examples:
                PRODUCT_SEARCH:
                    value:
                      - id: 1
                        name: "Product 1"
                      - id: 2
                        name: "Product 2"
components:
  schemas:
    Product:
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
        """.trimIndent(), "").toFeature().enableGenerativeTesting()

        val optionalQueryParam = "productName"

        val optionalQueryParamObserved = mutableListOf<String>()

        val results = productSpec.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                if(optionalQueryParam in request.queryParams.keys) {
                    optionalQueryParamObserved.add("yes")
                } else {
                    optionalQueryParamObserved.add("no")
                }

                return HttpResponse(200, body = parsedJSONArray("""[{"id": 1, "name": "Product 1"}, {"id": 2, "name": "Product 2"}]"""))
            }
        })

        println(optionalQueryParamObserved)
        assertThat(results.successCount).isPositive()
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `omitted mandatory query params should be generated in a test`() {
        val productSpec = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.3
info:
  title: Product Search Service
  description: API for searching product details
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product/search:
    get:
      summary: Search for product details
      parameters:
        - name: productName
          in: query
          description: Name of the product to search for
          required: false
          schema:
            type: string
        - name: productCategory
          in: query
          description: Category of the product to search for
          required: true
          schema:
            type: string
        - name: priceRange
          in: query
          description: Price range of the product to search for
          required: false
          schema:
            type: string
          examples:
            PRODUCT_SEARCH:
              value: "1000-2000"
      responses:
        200:
          description: Successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  ${"$"}ref: '#/components/schemas/Product'
              examples:
                PRODUCT_SEARCH:
                    value:
                      - id: 1
                        name: "Product 1"
                      - id: 2
                        name: "Product 2"
components:
  schemas:
    Product:
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
        """.trimIndent(), ""
        ).toFeature()

        val mandatoryQueryParam = "productCategory"

        val results = productSpec.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.queryParams.keys).contains(mandatoryQueryParam)

                return HttpResponse(
                    200,
                    body = parsedJSONArray("""[{"id": 1, "name": "Product 1"}, {"id": 2, "name": "Product 2"}]""")
                )
            }
        })

        assertThat(results.success()).isTrue()
    }
}
