package `in`.specmatic.conversions

import `in`.specmatic.core.Flags
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.JSONObjectPattern
import `in`.specmatic.core.value.*
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultValuesInOpenapiSpecification {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            System.setProperty("SCHEMA_EXAMPLE_DEFAULT", "true")
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            System.setProperty("SCHEMA_EXAMPLE_DEFAULT", "false")
        }
    }

    @Test
    fun `schema examples should be used as default values`() {
        val specification = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.1
            info:
              title: Employee API
              version: 1.0.0
            paths:
              /employees:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/Employee'
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
            components:
              schemas:
                Employee:
                  type: object
                  properties:
                    name:
                      type: string
                      example: 'Jane Doe'
                    age:
                      type: integer
                      format: int32
                      example: 35
                    salary:
                      type: number
                      format: double
                      nullable: true
                      example: 50000
                    salary_history:
                      type: array
                      items:
                        type: number
                        example: 1000
                    years_employed:
                      type: array
                      items:
                        type: number
                      example:
                        - 2021
                        - 2022
                        - 2023
                  required:
                    - name
                    - age
                    - salary
                    - years_employed
            """.trimIndent(), ""
        ).toFeature()

        val withGenerativeTestsEnabled = specification.copy(generativeTestingEnabled = true)

        val testRequestBodies = withGenerativeTestsEnabled.generateContractTestScenarios(emptyList()).filter {
            !it.isNegative
        }.map {
            it.httpRequestPattern.body.generate(it.resolver)
        }.filterIsInstance<JSONObjectPattern>()

        assertThat(testRequestBodies).allSatisfy {
            assertThat(it.pattern["name"]).isEqualTo(StringValue("Jane Doe"))
            assertThat(it.pattern["age"]).isEqualTo(NumberValue(35))
            assertThat(it.pattern["salary"]).isEqualTo(NumberValue(50000))

            val salaryHistory = it.pattern["salary_history"] as JSONArrayValue
            assertThat(salaryHistory.list).allSatisfy {
                assertThat(it).isEqualTo(NumberValue(1000))
            }

            val yearsEmployed = it.pattern["years_employed"] as JSONArrayValue
            assertThat(yearsEmployed.list).isEqualTo(
                JSONArrayValue(
                    listOf(
                        NumberValue(2021),
                        NumberValue(2022),
                        NumberValue(2023)
                    )
                )
            )
        }
    }

    @Test
    fun `named examples should be given preference over schema examples`() {
        val specification = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.1
            info:
              title: Employee API
              version: 1.0.0
            paths:
              /employees:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/Employee'
                        examples:
                          SUCCESS:
                            value:
                              name: 'John Doe'
                              age: 30
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: 'success'
            components:
              schemas:
                Employee:
                  type: object
                  properties:
                    name:
                      type: string
                      example: 'Jane Doe'
                    age:
                      type: integer
                      format: int32
                      example: 35
                    salary:
                      type: number
                      format: double
                      nullable: true
                      example: 50000
                  required:
                    - name
                    - age
            """.trimIndent(), ""
        ).toFeature()

        val withGenerativeTestsEnabled = specification.copy(generativeTestingEnabled = true)

        val generateContractTestScenarios = withGenerativeTestsEnabled.generateContractTestScenarios(emptyList())

        val positiveTests = generateContractTestScenarios.filter {
            !it.isNegative
        }

        val testRequestBodies = positiveTests.map {
            it.httpRequestPattern.body.generate(it.resolver)
        }

        assertThat(testRequestBodies).allSatisfy {
            assertThat(it).isInstanceOf(JSONObjectValue::class.java)

            it as JSONObjectValue

            assertThat(it.jsonObject["name"]).isEqualTo(StringValue("John Doe"))
            assertThat(it.jsonObject["age"]).isEqualTo(NumberValue(30))

            if ("salary" in it.jsonObject) {
                assertThat(it.jsonObject["salary"]).isEqualTo(NumberValue(50000))
            }
        }
    }
}