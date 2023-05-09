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
              TEST1:
                value:
                  data: abc
              TEST2:
                value:
                  data: abc
              TEST3:
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
                TEST1:
                  value: 10
                TEST2:
                  value: 10
                TEST3:
                  value: 10
              schema:
                type: number
        """.trimIndent(), "").toFeature()

    val contractTests = contract.generateContractTests(emptyList())

    @Test
    fun `should select tests containing the value of filterName in testDescription`() {
        val selected = selectTestsToRun(contractTests, "TEST1")
        assertThat(selected).hasSize(1)
        assertThat(selected.first().testDescription()).contains("TEST1")
    }

    @Test
    fun `should select tests whose testDescriptions contain any of the multiple comma separate values in filterName`() {
        val selected = selectTestsToRun(contractTests, "TEST1, TEST2")
        assertThat(selected).hasSize(2)
        assertThat(selected.map { it.testDescription() }).allMatch {
            it.contains("TEST1") || it.contains("TEST2")
        }
    }

    @Test
    fun `should omit tests containing the value of filterNotName in testDescription`() {
        val selected = selectTestsToRun(contractTests, filterNotName = "TEST1")
        assertThat(selected).hasSize(2)
        assertThat(selected.map { it.testDescription() }).allMatch {
            it.contains("TEST2") || it.contains("TEST3")
        }
    }

    @Test
    fun `should omit tests whose testDescriptions contain any of the multiple comma separate values in filterNotName`() {
        val selected = selectTestsToRun(contractTests, filterNotName = "TEST1, TEST2")
        assertThat(selected).hasSize(1)
        assertThat(selected.first().testDescription()).contains("TEST3")
    }
}
