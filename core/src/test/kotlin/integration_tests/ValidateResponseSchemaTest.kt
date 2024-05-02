package integration_tests

import `in`.specmatic.conversions.EnvironmentAndPropertiesConfiguration
import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.parsedJSONArray
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ValidateResponseSchemaTest {
    @Test
    fun `response body schema validation should fail if a key in the response example is missing in the actual response`() {
        val personSpec = """
            openapi: 3.0.3
            info:
              title: Person API
              version: 1.0.0
              description: API for creating new people
        
            servers:
              - url: http://localhost:5000  # Replace with your server URL
        
            paths:
              /person/{id}:
                get:
                  summary: Create a new person
                  parameters:
                    - in: path
                      required: true
                      name: id
                      schema:
                        type: number
                      examples:
                        GET_DETAILS:
                          value: 10
                  responses:
                    '200':
                      description: Person created successfully
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: "#/components/schemas/PersonData"
                          examples:
                            GET_DETAILS:
                              value:
                                name: "Sherlock Holmes"
                                address: "221B Baker Street"
            components:
              schemas:
                PersonData:
                  type: object
                  required:
                    - name
                  properties:
                    name:
                      type: string
                    address:
                      type: string        
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(personSpec, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse.ok(parsedJSONObject("""{"name": "Sherlock"}"""))
            }
        })

        assertThat(results.success()).withFailMessage("The tests passed, but they should have failed.").isFalse()
        assertThat(results.report()).withFailMessage(results.report()).contains("RESPONSE.BODY.address")
    }

    @Test
    fun `response body schema validation should be skipped if the response example value is empty`() {
        val personSpec = """
            openapi: 3.0.3
            info:
              title: Person API
              version: 1.0.0
              description: API for creating new people
        
            servers:
              - url: http://localhost:5000  # Replace with your server URL
        
            paths:
              /person/{id}:
                get:
                  summary: Create a new person
                  parameters:
                    - in: path
                      required: true
                      name: id
                      schema:
                        type: number
                      examples:
                        GET_DETAILS:
                          value: 10
                  responses:
                    '200':
                      description: Person created successfully
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: "#/components/schemas/PersonData"
                          examples:
                            GET_DETAILS:
                              value:
            components:
              schemas:
                PersonData:
                  type: object
                  required:
                    - name
                  properties:
                    name:
                      type: string
                    address:
                      type: string        
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(personSpec, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse.ok(parsedJSONObject("""{"name": "Sherlock"}"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `response body schema validation should not complain at all`() {
        val personSpec = """
            openapi: 3.0.3
            info:
              title: Person API
              version: 1.0.0
              description: API for creating new people
        
            servers:
              - url: http://localhost:5000  # Replace with your server URL
        
            paths:
              /person:
                get:
                  summary: Get person records
                  parameters:
                    - in: query
                      name: department
                      schema:
                        type: string
                      examples:
                        GET_DETAILS:
                          value: all
                  responses:
                    '200':
                      description: Person created successfully
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: "#/components/schemas/PersonRecords"
                          examples:
                            GET_DETAILS:
                              value:
                                - name: "Jack"
                                - name: "Jill"
                                  address: "Street"
            components:
              schemas:
                PersonRecords:
                  type: array
                  items:
                    ${"$"}ref: "#/components/schemas/PersonData"
                PersonData:
                  type: object
                  required:
                    - name
                  properties:
                    name:
                      type: string
                    address:
                      type: string        
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(personSpec, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.toLogString())
                return HttpResponse.ok(parsedJSONArray("""[{"name": "Sherlock"}]"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }
}