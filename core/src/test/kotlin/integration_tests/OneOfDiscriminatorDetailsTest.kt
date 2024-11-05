package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OneOfDiscriminatorDetailsTest {
    @Test
    fun `discriminator in oneOf should be honored even when children do not have it`() {
        val feature = OpenApiSpecification.fromYAML(
"""
openapi: 3.0.3
info:
  title: Product API
  version: 1.0.0
paths:
  /product/{id}:
    get:
      summary: Get product details by ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Successful response with product details
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Product'

components:
  schemas:
    Product:
      type: object
      oneOf:
        - ${"$"}ref: '#/components/schemas/Food'
        - ${"$"}ref: '#/components/schemas/Gadget'
      discriminator:
        propertyName: type
        mapping:
          food: '#/components/schemas/Food'
          gadget: '#/components/schemas/Gadget'

    ProductType:
      type: object
      properties:
        type:
          type: string

    Food:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            expirationDate:
              type: string
              format: date

    Gadget:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            warrantyPeriod:
              type: string
              format: date
""".trimIndent(), ""
        ).toFeature()

        val request = HttpRequest("GET", "/product/123")
        val response =
            HttpResponse.ok(parsedJSONObject("""{"id": "10", "name": "phone", "type": "gadget", "expirationDate": "2022-12-31"}"""))

        val scenario = feature.scenarios.first()

        val responseBody = scenario.httpResponsePattern.body.generate(scenario.resolver) as JSONObjectValue

        assertThat(responseBody.findFirstChildByPath("type")?.toStringLiteral()).isIn("gadget", "food")

        println(responseBody.toStringLiteral())

        val result = feature.matchResult(request, response)

        assertThat(result).withFailMessage(result.reportString()).isInstanceOf(Result.Success::class.java)

    }
}