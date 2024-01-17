package `in`.specmatic.test

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.TestConfig
import `in`.specmatic.test.reports.coverage.Endpoint
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

    private val contractTests = contract.generateContractTests(emptyList())

    @Test
    fun `should select tests containing the value of filterName in testDescription`() {
        val selected = selectTestsToRun(contractTests, "TEST1") { it.testDescription() }
        assertThat(selected).hasSize(1)
        assertThat(selected.first().testDescription()).contains("TEST1")
    }

    @Test
    fun `should select tests whose testDescriptions contain any of the multiple comma separate values in filterName`() {
        val selected = selectTestsToRun(contractTests, "TEST1, TEST2") { it.testDescription() }
        assertThat(selected).hasSize(2)
        assertThat(selected.map { it.testDescription() }).allMatch {
            it.contains("TEST1") || it.contains("TEST2")
        }
    }

    @Test
    fun `should omit tests containing the value of filterNotName in testDescription`() {
        val selected = selectTestsToRun(contractTests, filterNotName = "TEST1") { it.testDescription() }
        assertThat(selected).hasSize(2)
        assertThat(selected.map { it.testDescription() }).allMatch {
            it.contains("TEST2") || it.contains("TEST3")
        }
    }

    @Test
    fun `should omit tests whose testDescriptions contain any of the multiple comma separate values in filterNotName`() {
        val selected = selectTestsToRun(contractTests, filterNotName = "TEST1, TEST2") { it.testDescription() }
        assertThat(selected).hasSize(1)
        assertThat(selected.first().testDescription()).contains("TEST3")
    }

    @Test
    fun `should not filter the tests if filterName is empty`() {
        val selected = selectTestsToRun(contractTests, filterName = "") { it.testDescription() }

        assertThat(selected).hasSize(3)

        val descriptions = selected.map { it.testDescription() }.sorted()

        assertThat(descriptions[0]).contains("TEST1")
        assertThat(descriptions[1]).contains("TEST2")
        assertThat(descriptions[2]).contains("TEST3")
    }

    @Test
    fun `should not filter the tests if filterName is blank`() {
        val selected = selectTestsToRun(contractTests, filterName = " ") { it.testDescription() }

        assertThat(selected).hasSize(3)

        val descriptions = selected.map { it.testDescription() }.sorted()

        assertThat(descriptions[0]).contains("TEST1")
        assertThat(descriptions[1]).contains("TEST2")
        assertThat(descriptions[2]).contains("TEST3")
    }

    @Test
    fun `should not filter the tests if filterName is null`() {
        val selected = selectTestsToRun(contractTests, filterName = null) { it.testDescription() }

        assertThat(selected).hasSize(3)

        val descriptions = selected.map { it.testDescription() }.sorted()

        assertThat(descriptions[0]).contains("TEST1")
        assertThat(descriptions[1]).contains("TEST2")
        assertThat(descriptions[2]).contains("TEST3")
    }

    @Test
    fun `should not filter the tests if filterNotName is not found in any of the test names`() {
        val selected = selectTestsToRun(contractTests, filterNotName = "UNKNOWN_TEST_NAME_1, UNKNOWN_TEST_NAME_2") { it.testDescription() }

        assertThat(selected).hasSize(3)

        val descriptions = selected.map { it.testDescription() }.sorted()

        assertThat(descriptions[0]).contains("TEST1")
        assertThat(descriptions[1]).contains("TEST2")
        assertThat(descriptions[2]).contains("TEST3")
    }

    @Test
    fun `should not filter the tests if filterNotName is null`() {
        val selected = selectTestsToRun(contractTests, filterNotName = null) { it.testDescription() }

        assertThat(selected).hasSize(3)

        val descriptions = selected.map { it.testDescription() }.sorted()

        assertThat(descriptions[0]).contains("TEST1")
        assertThat(descriptions[1]).contains("TEST2")
        assertThat(descriptions[2]).contains("TEST3")
    }

    @Test
    fun `should not filter the tests if filterNotName is empty`() {
        val selected = selectTestsToRun(contractTests, filterNotName = "") { it.testDescription() }

        assertThat(selected).hasSize(3)

        val descriptions = selected.map { it.testDescription() }.sorted()

        assertThat(descriptions[0]).contains("TEST1")
        assertThat(descriptions[1]).contains("TEST2")
        assertThat(descriptions[2]).contains("TEST3")
    }

    @Test
    fun `should not filter the tests if filterNotName is blank`() {
        val selected = selectTestsToRun(contractTests, filterNotName = " ") { it.testDescription() }

        assertThat(selected).hasSize(3)

        val descriptions = selected.map { it.testDescription() }.sorted()

        assertThat(descriptions[0]).contains("TEST1")
        assertThat(descriptions[1]).contains("TEST2")
        assertThat(descriptions[2]).contains("TEST3")
    }
}
