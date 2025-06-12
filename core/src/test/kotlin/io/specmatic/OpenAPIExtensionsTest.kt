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
}