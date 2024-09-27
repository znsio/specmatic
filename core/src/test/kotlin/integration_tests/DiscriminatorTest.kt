package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscriminatorTest {
    @Test
    fun `when the discriminator property is missing from the spec the issue should be mentioned explicitly`() {
        val feature = OpenApiSpecification.fromYAML("""
            ---
            openapi: 3.0.3
            info:
              title: Vehicle API
              version: 1.0.0
            paths:
              /vehicle:
                post:
                  summary: Add a new vehicle
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/Vehicle'
                  responses:
                    '201':
                      description: Vehicle created successfully
                      content:
                        text/plain:
                          schema:
                            type: string

            components:
              schemas:
                VehicleType:
                  type: object

                Vehicle:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/VehicleType'
                    - type: object
                      properties:
                        seatingCapacity:
                          type: integer
                  discriminator:
                    propertyName: "type"
                    mapping:
                      "car": "#/components/schemas/Car"
                      "bike": "#/components/schemas/Bike"

                Car:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Vehicle'
                    - type: object
                      requried:
                        - gearType
                      properties:
                        gearType:
                          type: string
                Bike:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Vehicle'
                    - type: object
                      requried:
                        - headlampStyle
                      properties:
                        headlampStyle:
                          type: string
        """.trimIndent(), "").toFeature()

        val result = feature.matchResult(
            HttpRequest("POST", "/vehicle", body = parsedJSONObject("""{"type": "car", "seatingCapacity": 4, "gearType": "MT"}""")),
            HttpResponse(201, body = StringValue("success"))
        )

        assertThat(result.reportString()).doesNotContain("car, bike")
        assertThat(result.reportString()).contains("missing from the spec")
    }

    @Test
    fun `when the discriminator property is missing from the object the issue should be mentioned explicitly`() {
        val feature = OpenApiSpecification.fromYAML("""
            ---
            openapi: 3.0.3
            info:
              title: Vehicle API
              version: 1.0.0
            paths:
              /vehicle:
                post:
                  summary: Add a new vehicle
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/Vehicle'
                  responses:
                    '201':
                      description: Vehicle created successfully
                      content:
                        text/plain:
                          schema:
                            type: string

            components:
              schemas:
                VehicleType:
                  type: object
                  required:
                    - type
                  properties:
                    type:
                      type: string
                Vehicle:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/VehicleType'
                    - type: object
                      properties:
                        seatingCapacity:
                          type: integer
                  discriminator:
                    propertyName: "type"
                    mapping:
                      "car": "#/components/schemas/Car"
                      "bike": "#/components/schemas/Bike"

                Car:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Vehicle'
                    - type: object
                      requried:
                        - gearType
                      properties:
                        gearType:
                          type: string
                Bike:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Vehicle'
                    - type: object
                      requried:
                        - headlampStyle
                      properties:
                        headlampStyle:
                          type: string
        """.trimIndent(), "").toFeature()

        val result = feature.matchResult(
            HttpRequest("POST", "/vehicle", body = parsedJSONObject("""{"seatingCapacity": 4, "gearType": "MT"}""")),
            HttpResponse(201, body = StringValue("success"))
        )

        val discriminatorProperty = "type"
        assertThat(result.reportString()).contains("Discriminator property ${discriminatorProperty} is missing from the object")
    }

    @Test
    fun `discriminator property with incorrect value should be caught as an error`() {
        val feature = OpenApiSpecification.fromYAML("""
            ---
            openapi: 3.0.3
            info:
              title: Vehicle API
              version: 1.0.0
            paths:
              /vehicle:
                post:
                  summary: Add a new vehicle
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/Vehicle'
                  responses:
                    '201':
                      description: Vehicle created successfully
                      content:
                        text/plain:
                          schema:
                            type: string

            components:
              schemas:
                VehicleType:
                  type: object
                  required:
                    - type
                  properties:
                    type:
                      type: string
                Vehicle:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/VehicleType'
                    - type: object
                      properties:
                        seatingCapacity:
                          type: integer
                  discriminator:
                    propertyName: "type"
                    mapping:
                      "car": "#/components/schemas/Car"
                      "bike": "#/components/schemas/Bike"

                Car:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Vehicle'
                    - type: object
                      requried:
                        - gearType
                      properties:
                        gearType:
                          type: string
                Bike:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Vehicle'
                    - type: object
                      requried:
                        - headlampStyle
                      properties:
                        headlampStyle:
                          type: string
        """.trimIndent(), "").toFeature()

        val result = feature.matchResult(
            HttpRequest("POST", "/vehicle", body = parsedJSONObject("""{"type": "airplane", "seatingCapacity": 4, "gearType": "MT"}""")),
            HttpResponse(201, body = StringValue("success"))
        )

        assertThat(result.reportString()).contains("car, bike")
    }

}