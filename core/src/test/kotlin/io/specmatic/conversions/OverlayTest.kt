package io.specmatic.conversions

import io.specmatic.core.Result
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.ListPattern
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.pattern.resolvedHop
import io.specmatic.core.value.JSONArrayValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OverlayTest {
    companion object {
        fun assertKeyIsRequired(pattern: JSONObjectPattern, key: String) {
            assertThat(pattern.pattern.keys).contains(key)
            assertThat(pattern.pattern.keys).doesNotContain("$key?")
        }

        fun assertKeyIsOptional(pattern: JSONObjectPattern, key: String) {
            assertThat(pattern.pattern.keys).doesNotContain(key)
            assertThat(pattern.pattern.keys).contains("$key?")
        }
    }

    @Test
    fun `should be applied to resolved schema when properties are multi-level deep`() {
        val specContent = """
        openapi: 3.0.3
        info:
          title: Product API
          version: 1.0.0
        paths:
          /products/{productId}:
            get:
              summary: Retrieve a product by its ID
              description: Retrieve details of a product using its unique identifier.
              parameters:
                - name: productId
                  in: path
                  required: true
                  schema:
                    type: string
                  description: The unique identifier of the product to retrieve
              responses:
                '200':
                  description: Product retrieved successfully
                  content:
                    application/json:
                      schema:
                        ${'$'}ref: '#/components/schemas/ProductFinal'
        components:
          schemas:
            ProductInfo:
              type: object
              properties:
                price:
                  type: number
                  format: float
                  description: Price of the product
                category:
                  type: string
                  description: Category of the product
            BaseProduct:
              type: object
              properties:
                '@type':
                  type: string
                  description: Type of the product
                description:
                  type: string
                  description: Detailed description of the product
                href:
                  type: string
                  format: uri
                  description: URL to access the product details
                id:
                  type: string
                  description: Unique identifier for the product
            Product:
              allOf:
                - ${'$'}ref: "#/components/schemas/BaseProduct"
                - ${'$'}ref: "#/components/schemas/ProductInfo"
            ProductFinal:
              allOf:
                - ${'$'}ref: '#/components/schemas/Product'
                - type: object
                  properties:
                    name:
                      type: string
                      description: Name of the product
                    status:
                      type: string
                      description: Availability status of the product
                      enum: [available, out_of_stock, discontinued]
        """.trimIndent()
        val overlayContent = """
        overlay: 1.0.0
        actions:
        - target: ${'$'}.components.schemas
          update:
            AnyValue: {}
        - target: ${'$'}.components.schemas.ProductFinal.allOf
          update:
            required:
            - '@type'
            - description
            - id
            - price
            - category
            - name
            - status
            properties:
              '@type':
                type: '#/components/schemas/AnyValue'
              description:
                type: '#/components/schemas/AnyValue'
              id:
                type: '#/components/schemas/AnyValue'
              price:
                type: '#/components/schemas/AnyValue'
              category:
                type: '#/components/schemas/AnyValue'
              name:
                type: '#/components/schemas/AnyValue'
              status:
                type: '#/components/schemas/AnyValue'
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
        val scenario = feature.scenarios.first { it.path == "/products/(productId:string)" }
        val resolvedBodyPattern = resolvedHop(scenario.httpResponsePattern.body, scenario.resolver) as JSONObjectPattern

        val requiredKeys = listOf("@type", "description", "id", "price", "category", "name", "status")
        requiredKeys.forEach { key ->
            assertKeyIsRequired(resolvedBodyPattern, key)
        }
    }

    @Test
    fun `data type mismatch errors shouldn't repeat on an overlay applied specification`() {
        val specContent = """
        openapi: 3.0.3
        info:
          title: Product API
          version: 1.0.0
        paths:
          /products:
            get:
              summary: Retrieve products
              description: Retrieve products
              responses:
                '200':
                  description: Products retrieved successfully
                  content:
                    application/json:
                      schema:
                        type: array
                        items:
                          ${'$'}ref: '#/components/schemas/Product'
          /products/{productId}:
            get:
              summary: Retrieve a product by its ID
              description: Retrieve details of a product using its unique identifier.
              parameters:
                - name: productId
                  in: path
                  required: true
                  schema:
                    type: string
                  description: The unique identifier of the product to retrieve
              responses:
                '200':
                  description: Product retrieved successfully
                  content:
                    application/json:
                      schema:
                        ${'$'}ref: '#/components/schemas/Product'
        components:
          schemas:
            BaseProduct:
              type: object
              properties:
                '@type':
                  type: string
                  description: Type of the product
                description:
                  type: string
                  description: Detailed description of the product
                href:
                  type: string
                  format: uri
                  description: URL to access the product details
                id:
                  type: string
                  description: Unique identifier for the product
                price:
                  type: number
                  format: float
                  description: Price of the product
                category:
                  type: string
                  description: Category of the product
              required:
                - '@type'
                - description
                - href
                - id
                - price
                - category
            Product:
              allOf:
                - ${'$'}ref: '#/components/schemas/BaseProduct'
                - type: object
                  properties:
                    name:
                      type: string
                      description: Name of the product
                    status:
                      type: string
                      description: Availability status of the product
                      enum: [available, out_of_stock, discontinued]
        """.trimIndent()
        val overlayContent = """
        overlay: 1.0.0
        actions:
        - target: ${'$'}.components.schemas.BaseProduct.required[?(@ == 'category' || @ == 'price')]
          remove: true
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
        // NOTE: BaseProduct.id mutated from string to number
        val incorrectDataTypeValue = parsedJSONObject("""{
            "@type": "Product",
            "description": "Product description",
            "href": "http://example.com",
            "id": 123,
            "name": "Product name",
            "status": "available"
        }""".trimIndent())

        val objectScenario = feature.scenarios.first { it.path == "/products/(productId:string)" }
        val objectResponseBody = resolvedHop(objectScenario.httpResponsePattern.body, objectScenario.resolver) as JSONObjectPattern

        val objectResult = objectResponseBody.matches(incorrectDataTypeValue, objectScenario.resolver)
        assertThat(objectResult.reportString()).isEqualToIgnoringNewLines(
        """
        >> id

           Expected string, actual was 123 (number)
        """.trimIndent())
        assertThat(objectResult).isInstanceOf(Result.Failure::class.java)

        val arrayScenario = feature.scenarios.first { it.path == "/products" }
        val arrayResponseBody = resolvedHop(arrayScenario.httpResponsePattern.body, objectScenario.resolver) as ListPattern

        val arrayResult = arrayResponseBody.matches(JSONArrayValue(listOf(incorrectDataTypeValue, incorrectDataTypeValue)), arrayScenario.resolver)
        assertThat(arrayResult.reportString()).isEqualToIgnoringNewLines(
        """
        >> [0].id

           Expected string, actual was 123 (number)
        
        >> [1].id
        
           Expected string, actual was 123 (number)
        """.trimIndent())
        assertThat(arrayResult).isInstanceOf(Result.Failure::class.java)
    }

    @Nested
    inner class UpdateTest {
        /*
    * NOTE: All tests utilize the Any type for updating optionality, even if not explicitly stated.
    */

        @Test
        fun `should mark properties as required previously optional in the resolved schema for Object`() {
            val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /products/{productId}:
                get:
                  summary: Retrieve a product by its ID
                  description: Retrieve details of a product using its unique identifier.
                  parameters:
                    - name: productId
                      in: path
                      required: true
                      schema:
                        type: string
                      description: The unique identifier of the product to retrieve
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Product'
            components:
              schemas:
                BaseProduct:
                  type: object
                  properties:
                    '@type':
                      type: string
                      description: Type of the product
                    description:
                      type: string
                      description: Detailed description of the product
                    href:
                      type: string
                      format: uri
                      description: URL to access the product details
                    id:
                      type: string
                      description: Unique identifier for the product
                    price:
                      type: number
                      format: float
                      description: Price of the product
                    category:
                      type: string
                      description: Category of the product
                Product:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/BaseProduct'
                    - type: object
                      properties:
                        name:
                          type: string
                          description: Name of the product
                        status:
                          type: string
                          description: Availability status of the product
                          enum: [available, out_of_stock, discontinued]
            """.trimIndent()
            val overlayContent = """
            overlay: 1.0.0
            actions:
            - target: ${'$'}.components.schemas
              update:
                AnyValue: {}
            - target: ${'$'}.components.schemas.Product.allOf
              update:
                type: object
                required:
                - '@type'
                - description
                - id
                properties:
                  '@type':
                    type: '#/components/schemas/AnyValue'
                  description:
                    type: '#/components/schemas/AnyValue'
                  id:
                    type: '#/components/schemas/AnyValue'
            """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
            val scenario = feature.scenarios.first { it.path == "/products/(productId:string)" }
            val resolvedBodyPattern = resolvedHop(scenario.httpResponsePattern.body, scenario.resolver) as JSONObjectPattern

            val requiredKeys = listOf("@type", "description", "id")
            requiredKeys.forEach { key ->
                assertKeyIsRequired(resolvedBodyPattern, key)
            }

            val optionalKeys = listOf("href", "price", "category", "name", "status")
            optionalKeys.forEach { key ->
                assertKeyIsOptional(resolvedBodyPattern, key)
            }
        }

        @Test
        fun `should mark properties as required previously optional in the resolved schema for Array`() {
            val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /products:
                get:
                  summary: Retrieve products
                  description: Retrieve products
                  responses:
                    '200':
                      description: Products retrieved successfully
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              ${'$'}ref: '#/components/schemas/Product'
            components:
              schemas:
                BaseProduct:
                  type: object
                  properties:
                    '@type':
                      type: string
                      description: Type of the product
                    description:
                      type: string
                      description: Detailed description of the product
                    href:
                      type: string
                      format: uri
                      description: URL to access the product details
                    id:
                      type: string
                      description: Unique identifier for the product
                    price:
                      type: number
                      format: float
                      description: Price of the product
                    category:
                      type: string
                      description: Category of the product
                Product:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/BaseProduct'
                    - type: object
                      properties:
                        name:
                          type: string
                          description: Name of the product
                        status:
                          type: string
                          description: Availability status of the product
                          enum: [available, out_of_stock, discontinued]
            """.trimIndent()
            val overlayContent = """
            overlay: 1.0.0
            actions:
            - target: ${'$'}.components.schemas
              update:
                AnyValue: {}
            - target: ${'$'}.components.schemas.Product.allOf
              update:
                type: object
                required:
                - '@type'
                - description
                - id
                properties:
                  '@type':
                    type: '#/components/schemas/AnyValue'
                  description:
                    type: '#/components/schemas/AnyValue'
                  id:
                    type: '#/components/schemas/AnyValue'
            """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
            val scenario = feature.scenarios.first { it.path == "/products" }
            val bodyPattern = scenario.httpResponsePattern.body as ListPattern
            val resolvedBodyPattern = resolvedHop(bodyPattern.pattern, scenario.resolver) as JSONObjectPattern

            val requiredKeys = listOf("@type", "description", "id")
            requiredKeys.forEach { key ->
                assertKeyIsRequired(resolvedBodyPattern, key)
            }

            val optionalKeys = listOf("href", "price", "category", "name", "status")
            optionalKeys.forEach { key ->
                assertKeyIsOptional(resolvedBodyPattern, key)
            }
        }

        @Test
        fun `should keep properties as required previously also required in the resolved schema`() {
            val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /products/{productId}:
                get:
                  summary: Retrieve a product by its ID
                  description: Retrieve details of a product using its unique identifier.
                  parameters:
                    - name: productId
                      in: path
                      required: true
                      schema:
                        type: string
                      description: The unique identifier of the product to retrieve
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Product'
            components:
              schemas:
                BaseProduct:
                  type: object
                  properties:
                    '@type':
                      type: string
                      description: Type of the product
                    description:
                      type: string
                      description: Detailed description of the product
                    href:
                      type: string
                      format: uri
                      description: URL to access the product details
                    id:
                      type: string
                      description: Unique identifier for the product
                    price:
                      type: number
                      format: float
                      description: Price of the product
                    category:
                      type: string
                      description: Category of the product
                  required:
                    - '@type'
                    - description
                    - id
                    - href
                    - price
                    - category
                Product:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/BaseProduct'
                    - type: object
                      properties:
                        name:
                          type: string
                          description: Name of the product
                        status:
                          type: string
                          description: Availability status of the product
                          enum: [available, out_of_stock, discontinued]
            """.trimIndent()
            val overlayContent = """
            overlay: 1.0.0
            actions:
            - target: ${'$'}.components.schemas
              update:
                AnyValue: {}
            - target: ${'$'}.components.schemas.Product.allOf
              update:
                type: object
                required:
                - '@type'
                - description
                - id
                properties:
                  '@type':
                    type: '#/components/schemas/AnyValue'
                  description:
                    type: '#/components/schemas/AnyValue'
                  id:
                    type: '#/components/schemas/AnyValue'
            """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
            val scenario = feature.scenarios.first { it.path == "/products/(productId:string)" }
            val resolvedBodyPattern = resolvedHop(scenario.httpResponsePattern.body, scenario.resolver) as JSONObjectPattern

            val requiredKeys = listOf("@type", "description", "id", "href", "price", "category")
            requiredKeys.forEach { key ->
                assertKeyIsRequired(resolvedBodyPattern, key)
            }

            val optionalKeys = listOf("name", "status")
            optionalKeys.forEach { key ->
                assertKeyIsOptional(resolvedBodyPattern, key)
            }
        }

        @Test
        fun `should be applied to all matching json paths specified in the target`() {
            val specContent = """
        openapi: 3.0.3
        info:
          title: Product and Order API
          version: 1.0.0
        paths:
          /products/{productId}:
            get:
              summary: Retrieve a product by its ID
              description: Retrieve details of a product using its unique identifier.
              parameters:
                - name: productId
                  in: path
                  required: true
                  schema:
                    type: string
                  description: The unique identifier of the product to retrieve
              responses:
                '200':
                  description: Product retrieved successfully
                  content:
                    application/json:
                      schema:
                        ${'$'}ref: '#/components/schemas/Product'
          /orders/{orderId}:
            get:
              summary: Retrieve an order by its ID
              description: Retrieve details of an order using its unique identifier.
              parameters:
                - name: orderId
                  in: path
                  required: true
                  schema:
                    type: string
                  description: The unique identifier of the order to retrieve
              responses:
                '200':
                  description: Order retrieved successfully
                  content:
                    application/json:
                      schema:
                        ${'$'}ref: '#/components/schemas/Order'
        components:
          schemas:
            Base:
              type: object
              properties:
                '@type':
                  type: string
                  description: Type of the product
                description:
                  type: string
                  description: Detailed description of the product
                href:
                  type: string
                  format: uri
                  description: URL to access the product details
                id:
                  type: string
                  description: Unique identifier for the product
                price:
                  type: number
                  format: float
                  description: Price of the product
                category:
                  type: string
                  description: Category of the product
            Product:
              allOf:
                - ${'$'}ref: '#/components/schemas/Base'
                - type: object
                  properties:
                    name:
                      type: string
                      description: Name of the product
                    status:
                      type: string
                      description: Availability status of the product
                      enum: [available, out_of_stock, discontinued]
            Order:
              allOf:
                - ${'$'}ref: '#/components/schemas/Base'
                - type: object
                  properties:
                    orderId:
                      type: string
                      description: Unique identifier for the order
                    quantity:
                      type: integer
                      description: Quantity of the product ordered
                    status:
                      type: string
                      description: Order status
                      enum: [pending, shipped, delivered, canceled]
        """.trimIndent()
            val overlayContent = """
        overlay: 1.0.0
        actions:
        - target: ${'$'}.components.schemas
          update:
            AnyValue: {}
        - target: ${'$'}.components.schemas.['Product','Order'].allOf
          update:
            type: object
            required:
              - "@type"
              - description
              - id
              - href
              - price
            properties:
              "@type":
                type: "#/components/schemas/AnyValue"
              description:
                type: "#/components/schemas/AnyValue"
              id:
                type: "#/components/schemas/AnyValue"
              href:
                type: "#/components/schemas/AnyValue"
              price:
                type: "#/components/schemas/AnyValue"
        """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
            val requiredKeys = listOf("@type", "description", "id", "description", "price")

            val productScenario = feature.scenarios.first { it.path == "/products/(productId:string)" }
            val productResolvedBodyPattern = resolvedHop(productScenario.httpResponsePattern.body, productScenario.resolver) as JSONObjectPattern
            requiredKeys.forEach { key ->
                assertKeyIsRequired(productResolvedBodyPattern, key)
            }

            val productOptionalKeys = listOf("category", "name", "status")
            productOptionalKeys.forEach { key ->
                assertKeyIsOptional(productResolvedBodyPattern, key)
            }

            val orderScenario = feature.scenarios.first { it.path == "/orders/(orderId:string)" }
            val orderResolvedBodyPattern = resolvedHop(orderScenario.httpResponsePattern.body, orderScenario.resolver) as JSONObjectPattern
            requiredKeys.forEach { key ->
                assertKeyIsRequired(orderResolvedBodyPattern, key)
            }

            val orderOptionalKeys = listOf("category", "orderId", "quantity", "status")
            orderOptionalKeys.forEach { key ->
                assertKeyIsOptional(orderResolvedBodyPattern, key)
            }
        }
    }

    @Nested
    inner class RemoveTest {
        @Test
        fun `should mark properties as optional previously required in the resolved allOf schema`() {
            val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /products/{productId}:
                get:
                  summary: Retrieve a product by its ID
                  description: Retrieve details of a product using its unique identifier.
                  parameters:
                    - name: productId
                      in: path
                      required: true
                      schema:
                        type: string
                      description: The unique identifier of the product to retrieve
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Product'
            components:
              schemas:
                BaseProduct:
                  type: object
                  properties:
                    '@type':
                      type: string
                      description: Type of the product
                    description:
                      type: string
                      description: Detailed description of the product
                    href:
                      type: string
                      format: uri
                      description: URL to access the product details
                    id:
                      type: string
                      description: Unique identifier for the product
                    price:
                      type: number
                      format: float
                      description: Price of the product
                    category:
                      type: string
                      description: Category of the product
                  required:
                    - '@type'
                    - description
                    - href
                    - id
                    - price
                    - category
                Product:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/BaseProduct'
                    - type: object
                      properties:
                        name:
                          type: string
                          description: Name of the product
                        status:
                          type: string
                          description: Availability status of the product
                          enum: [available, out_of_stock, discontinued]
            """.trimIndent()
            val overlayContent = """
            overlay: 1.0.0
            actions:
            - target: ${'$'}.components.schemas.BaseProduct.required[?(@ == 'category' || @ == 'price')]
              remove: true
            """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
            val scenario = feature.scenarios.first { it.path == "/products/(productId:string)" }
            val resolvedBodyPattern = resolvedHop(scenario.httpResponsePattern.body, scenario.resolver) as JSONObjectPattern

            val optionalKeys = listOf("price", "category", "name", "status")
            optionalKeys.forEach { key ->
                assertKeyIsOptional(resolvedBodyPattern, key)
            }

            val requiredKeys = listOf("@type", "description", "href", "id")
            requiredKeys.forEach { key ->
                assertKeyIsRequired(resolvedBodyPattern, key)
            }
        }

        @Test
        fun `should mark properties as optional previously required in the resolved schema for Array`() {
            val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /products:
                get:
                  summary: Retrieve products
                  description: Retrieve products
                  responses:
                    '200':
                      description: Products retrieved successfully
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              ${'$'}ref: '#/components/schemas/Product'
            components:
              schemas:
                BaseProduct:
                  type: object
                  properties:
                    '@type':
                      type: string
                      description: Type of the product
                    description:
                      type: string
                      description: Detailed description of the product
                    href:
                      type: string
                      format: uri
                      description: URL to access the product details
                    id:
                      type: string
                      description: Unique identifier for the product
                    price:
                      type: number
                      format: float
                      description: Price of the product
                    category:
                      type: string
                      description: Category of the product
                  required:
                    - '@type'
                    - description
                    - href
                    - id
                    - price
                    - category
                Product:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/BaseProduct'
                    - type: object
                      properties:
                        name:
                          type: string
                          description: Name of the product
                        status:
                          type: string
                          description: Availability status of the product
                          enum: [available, out_of_stock, discontinued]
            """.trimIndent()
            val overlayContent = """
            overlay: 1.0.0
            actions:
            - target: ${'$'}.components.schemas.BaseProduct.required[?(@ == 'category' || @ == 'price')]
              remove: true
            """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
            val scenario = feature.scenarios.first { it.path == "/products" }
            val bodyPattern = scenario.httpResponsePattern.body as ListPattern
            val resolvedBodyPattern = resolvedHop(bodyPattern.pattern, scenario.resolver) as JSONObjectPattern

            val optionalKeys = listOf("price", "category", "name", "status")
            optionalKeys.forEach { key ->
                assertKeyIsOptional(resolvedBodyPattern, key)
            }

            val requiredKeys = listOf("@type", "description", "href", "id")
            requiredKeys.forEach { key ->
                assertKeyIsRequired(resolvedBodyPattern, key)
            }
        }

        @Test
        fun `should be applied to all matching json paths specified in the target`() {
            val specContent = """
            openapi: 3.0.3
            info:
              title: Product and Order API
              version: 1.0.0
            paths:
              /products/{productId}:
                get:
                  summary: Retrieve a product by its ID
                  description: Retrieve details of a product using its unique identifier.
                  parameters:
                    - name: productId
                      in: path
                      required: true
                      schema:
                        type: string
                      description: The unique identifier of the product to retrieve
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Product'
              /orders/{orderId}:
                get:
                  summary: Retrieve an order by its ID
                  description: Retrieve details of an order using its unique identifier.
                  parameters:
                    - name: orderId
                      in: path
                      required: true
                      schema:
                        type: string
                      description: The unique identifier of the order to retrieve
                  responses:
                    '200':
                      description: Order retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Order'
            components:
              schemas:
                Base:
                  type: object
                  properties:
                    '@type':
                      type: string
                      description: Type of the product
                    description:
                      type: string
                      description: Detailed description of the product
                    href:
                      type: string
                      format: uri
                      description: URL to access the product details
                    id:
                      type: string
                      description: Unique identifier for the product
                    price:
                      type: number
                      format: float
                      description: Price of the product
                    category:
                      type: string
                      description: Category of the product
                Product:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Base'
                    - type: object
                      properties:
                        name:
                          type: string
                          description: Name of the product
                        status:
                          type: string
                          description: Availability status of the product
                          enum: [available, out_of_stock, discontinued]
                      required:
                        - name
                        - status
                Order:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Base'
                    - type: object
                      properties:
                        orderId:
                          type: string
                          description: Unique identifier for the order
                        quantity:
                          type: integer
                          description: Quantity of the product ordered
                        status:
                          type: string
                          description: Order status
                          enum: [pending, shipped, delivered, canceled]
                      required:
                        - orderId
                        - quantity
                        - status
                """.trimIndent()
            val overlayContent = """
            overlay: 1.0.0
            actions:
            - target: ${'$'}.components.schemas.['Product','Order'].allOf[1].required[?(@ == 'status')]
              remove: true
            """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(specContent, "", overlayContent = overlayContent).toFeature()
            val optionalKeys = listOf("@type", "description", "id", "description", "price", "category", "status")

            val productScenario = feature.scenarios.first { it.path == "/products/(productId:string)" }
            val productResolvedBodyPattern = resolvedHop(productScenario.httpResponsePattern.body, productScenario.resolver) as JSONObjectPattern
            optionalKeys.forEach { key ->
                assertKeyIsOptional(productResolvedBodyPattern, key)
            }

            val productRequiredKeys = listOf("name")
            productRequiredKeys.forEach { key ->
                assertKeyIsRequired(productResolvedBodyPattern, key)
            }

            val orderScenario = feature.scenarios.first { it.path == "/orders/(orderId:string)" }
            val orderResolvedBodyPattern = resolvedHop(orderScenario.httpResponsePattern.body, orderScenario.resolver) as JSONObjectPattern
            optionalKeys.forEach { key ->
                assertKeyIsOptional(orderResolvedBodyPattern, key)
            }

            val orderRequiredKeys = listOf("orderId", "quantity")
            orderRequiredKeys.forEach { key ->
                assertKeyIsRequired(orderResolvedBodyPattern, key)
            }
        }
    }
}