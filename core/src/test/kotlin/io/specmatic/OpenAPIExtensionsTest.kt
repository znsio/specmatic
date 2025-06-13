package io.specmatic

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.resolvedHop
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenAPIExtensionsTest {

    @Test
    fun `should store extensions for a JSONObjectPattern`() {
        val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /product:
                get:
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Product'
            components:
              schemas:
                Product:
                  type: object
                  x-id-field: productId
                  properties:
                    productId:
                      type: string
                    price:
                      type: number
                    category:
                      type: string
        """.trimIndent()


        val feature = OpenApiSpecification.fromYAML(specContent, "").toFeature()
        val scenario = feature.scenarios.first { it.path == "/product" }

        val responseBodyPattern = scenario.httpResponsePattern.body
        val resolvedResponseBodyPattern = resolvedHop(responseBodyPattern, scenario.resolver) as JSONObjectPattern

        assertThat(resolvedResponseBodyPattern.extensions["x-id-field"]).isEqualTo("productId")
    }

    @Test
    fun `should store extensions from allOfs in the resolved JSONObjectPattern where the extensions are present in an inline schema`() {
        val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /product:
                get:
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/ExtendedProduct'
            components:
              schemas:
                BaseProduct:
                  type: object
                  properties:
                    category:
                      type: string
                    price:
                      type: number
                  required:
                    - category 
                    - price
                ExtendedProduct:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/BaseProduct'
                    - type: object
                      x-id-field: productId
                      properties:
                        productId:
                          type: string
                      required:
                        - productId 
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specContent, "").toFeature()
        val scenario = feature.scenarios.first { it.path == "/product" }

        val responseBodyPattern = scenario.httpResponsePattern.body
        val resolvedResponseBodyPattern = resolvedHop(responseBodyPattern, scenario.resolver) as JSONObjectPattern

        assertThat(resolvedResponseBodyPattern.pattern.keys).containsAll(listOf("productId", "price", "category"))
        assertThat(resolvedResponseBodyPattern.extensions["x-id-field"]).isEqualTo("productId")
    }

    @Test
    fun `should store extensions from allOfs in the resolved JSONObjectPattern where the extensions are present in a referenced schema`() {
        val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /product:
                get:
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/ExtendedProduct'
            components:
              schemas:
                ProductId:
                  type: object
                  x-id-field: productId
                  properties:
                     productId:
                       type: string
                  required:
                     - productId
                ExtendedProduct:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/ProductId'
                    - type: object
                      properties:
                        category:
                          type: string
                        price:
                          type: number
                      required:
                        - category 
                        - price 
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specContent, "").toFeature()
        val scenario = feature.scenarios.first { it.path == "/product" }

        val responseBodyPattern = scenario.httpResponsePattern.body
        val resolvedResponseBodyPattern = resolvedHop(responseBodyPattern, scenario.resolver) as JSONObjectPattern

        assertThat(resolvedResponseBodyPattern.pattern.keys).containsAll(listOf("productId", "price", "category"))
        assertThat(resolvedResponseBodyPattern.extensions["x-id-field"]).isEqualTo("productId")
    }

    @Test
    fun `should preserve extensions from oneOf options within allOf`() {
        val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /product:
                get:
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/ExtendedProduct'
            components:
              schemas:
                ExtendedProduct:
                  allOf:
                    - type: object
                      properties:
                        data:
                          type: string
                      required:
                        - data
                    - type: object
                      properties:
                        characteristic:
                          oneOf:
                            - type: object
                              x-property-1: value1
                              properties:
                                category:
                                  type: string
                                price:
                                  type: number
                              required:
                                - category 
                                - price 
                            - type: object
                              x-property-2: value2
                              properties:
                                category:
                                  type: string
                                price:
                                  type: number
                              required:
                                - category 
                                - price
                      required:
                        - characteristic
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specContent, "").toFeature()
        val scenario = feature.scenarios.first { it.path == "/product" }

        val responseBodyPattern = scenario.httpResponsePattern.body
        val resolvedResponseBodyPattern = resolvedHop(responseBodyPattern, scenario.resolver) as JSONObjectPattern

        // Get the characteristic property pattern  
        val characteristicPattern = resolvedResponseBodyPattern.pattern["characteristic"] ?: resolvedResponseBodyPattern.pattern["characteristic?"]
        assertThat(characteristicPattern).isNotNull

        // The characteristic should be an AnyPattern with extensions from both oneOf options
        val resolvedCharacteristicPattern = resolvedHop(characteristicPattern!!, scenario.resolver)
        
        // Verify extensions are preserved in the AnyPattern
        if (resolvedCharacteristicPattern is AnyPattern) {
            // Check that extensions from both oneOf options are present
            assertThat(resolvedCharacteristicPattern.extensions).containsEntry("x-property-1", "value1")
            assertThat(resolvedCharacteristicPattern.extensions).containsEntry("x-property-2", "value2")
        } else {
            throw AssertionError("Expected AnyPattern but got ${resolvedCharacteristicPattern::class.simpleName}")
        }
    }

    @Test
    fun `should preserve extensions from top-level oneOf options`() {
        val specContent = """
            openapi: 3.0.3
            info:
              title: Product API
              version: 1.0.0
            paths:
              /product:
                get:
                  responses:
                    '200':
                      description: Product retrieved successfully
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/ExtendedProduct'
            components:
              schemas:
                ExtendedProduct:
                  oneOf:
                    - type: object
                      x-property-1: value1
                      properties:
                        category:
                          type: string
                        price:
                          type: number
                      required:
                        - category 
                        - price 
                    - type: object
                      x-property-2: value2
                      properties:
                        category:
                          type: string
                        price:
                          type: number
                      required:
                        - category 
                        - price
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specContent, "").toFeature()
        val scenario = feature.scenarios.first { it.path == "/product" }

        val responseBodyPattern = scenario.httpResponsePattern.body
        val resolvedResponseBodyPattern = resolvedHop(responseBodyPattern, scenario.resolver)

        // The response body pattern should be an AnyPattern with extensions from both oneOf options
        if (resolvedResponseBodyPattern is AnyPattern) {
            // Check that extensions from both oneOf options are present
            assertThat(resolvedResponseBodyPattern.extensions).containsEntry("x-property-1", "value1")
            assertThat(resolvedResponseBodyPattern.extensions).containsEntry("x-property-2", "value2")
        } else {
            throw AssertionError("Expected AnyPattern but got ${resolvedResponseBodyPattern::class.simpleName}")
        }
    }
}