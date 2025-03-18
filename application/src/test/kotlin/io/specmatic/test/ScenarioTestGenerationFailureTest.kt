package io.specmatic.test

import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.HttpPathPattern
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.HttpResponsePattern
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.pattern.ContractException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScenarioTestGenerationFailureTest {
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

        val testGenerationFailure =
            ScenarioTestGenerationFailure(scenario, Result.Failure("error"))
        val scenarioMetadata = testGenerationFailure.toScenarioMetadata()

        assertThat(scenarioMetadata.method).isEqualTo("POST")
        assertThat(scenarioMetadata.path).isEqualTo("/createProduct")
        assertThat(scenarioMetadata.query).isEqualTo(setOf("productId", "orderId"))
        assertThat(scenarioMetadata.header).isEqualTo(setOf("Authorization", "X-Request-ID"))
        assertThat(scenarioMetadata.statusCode).isEqualTo(200)
        assertThat(scenarioMetadata.exampleName).isEqualTo("example")

    }
}