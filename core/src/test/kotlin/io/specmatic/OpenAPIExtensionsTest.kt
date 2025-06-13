package io.specmatic

import io.specmatic.conversions.OpenApiSpecification
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
}