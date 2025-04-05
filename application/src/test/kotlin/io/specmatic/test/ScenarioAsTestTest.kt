package io.specmatic.test

import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScenarioAsTestTest {
    @Test
    fun `should return scenario metadata`() {
        val httpRequestPattern = mockk<HttpRequestPattern> {
            every {
                getHeaderKeys()
            } returns setOf("Authorization", "X-Request-ID")

            every {
                getQueryParamKeys()
            } returns setOf("productId", "orderId")

            every { method } returns "POST"
            every { httpPathPattern } returns HttpPathPattern(emptyList(), "/createProduct")
        }

        val scenario = Scenario(
            "",
            httpRequestPattern,
            HttpResponsePattern(status = 200),
            exampleName = "example"
        )

        val scenarioAsTest = ScenarioAsTest(scenario, Feature(name = ""), flagsBased = DefaultStrategies, originalScenario = scenario, baseURL = "")
        val scenarioMetadata = scenarioAsTest.toScenarioMetadata()

        assertThat(scenarioMetadata.method).isEqualTo("POST")
        assertThat(scenarioMetadata.path).isEqualTo("/createProduct")
        assertThat(scenarioMetadata.query).isEqualTo(setOf("productId", "orderId"))
        assertThat(scenarioMetadata.header).isEqualTo(setOf("Authorization", "X-Request-ID"))
        assertThat(scenarioMetadata.statusCode).isEqualTo(200)
        assertThat(scenarioMetadata.exampleName).isEqualTo("example")
    }
}