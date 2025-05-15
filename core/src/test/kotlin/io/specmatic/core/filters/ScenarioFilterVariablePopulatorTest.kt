package io.specmatic.core.filters

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.filters.ExpressionStandardizer.Companion.filterToEvalEx
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScenarioFilterVariablePopulatorTest {
    private val featureWithQueryParamExamples = OpenApiSpecification.fromYAML(
        """
            openapi: 3.0.1
            info:
              title: Data API
              version: "1"
            paths:
              /:
                get:
                  summary: Data
                  parameters:
                    - name: type
                      schema:
                        type: string
                      in: query
                      required: true
                      examples:
                        QUERY_SUCCESS:
                          value: data
                  responses:
                    "200":
                      description: Data
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              type:
                                string
                          examples:
                            QUERY_SUCCESS:
                              value: ["one", "two"]
                """.trimIndent(), ""
    ).toFeature()

    @Test
    fun `foo`() {
        val scenario = featureWithQueryParamExamples.scenarios[0]
        val scenarioFilterVariablePopulator = ScenarioFilterVariablePopulator(scenario)
        val expression = filterToEvalEx("PARAMETERS.HEADER.CONTENT-TYPE='application/json'")
        scenarioFilterVariablePopulator.populateExpressionData(expression)
        assertThat(expression.evaluate().booleanValue).isTrue()
    }
}
