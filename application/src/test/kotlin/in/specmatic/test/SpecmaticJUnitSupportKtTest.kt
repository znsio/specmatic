package `in`.specmatic.test

import `in`.specmatic.conversions.OpenApiSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpecmaticJUnitSupportKtTest {
    val contract = OpenApiSpecification.fromYAML("""
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            examples:
              INCLUDE1:
                value:
                  data: abc
              INCLUDE2:
                value:
                  data: abc
              EXCLUDE:
                value:
                  data: abc
            schema:
              type: object
              properties:
                data:
                  type: string
              required:
                - data
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              examples:
                INCLUDE1:
                  value: 10
                INCLUDE2:
                  value: 10
                EXCLUDE:
                  value: 10
              schema:
                type: number
        """.trimIndent(), "").toFeature()

    val contractTests = contract.generateContractTests(emptyList())
    @Test
    fun `should select tests containing the value of filterName in testDescription`() {
        val selected = selectTestsToRun("INCLUDE1", contractTests)
        assertThat(selected).hasSize(1)
        assertThat(selected.first().testDescription()).contains("INCLUDE1")
    }

    @Test
    fun `should select tests whose testDescriptions contain any of the multiple comma separate values in filterName`() {
        val selected = selectTestsToRun("INCLUDE1, INCLUDE2", contractTests)
        assertThat(selected).hasSize(2)
        assertThat(selected.map { it.testDescription() }).allMatch {
            it.contains("INCLUDE1") || it.contains("INCLUDE2")
        }
    }
}
