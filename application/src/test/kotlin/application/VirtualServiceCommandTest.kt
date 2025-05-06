package application

import org.junit.jupiter.api.Assertions.assertEquals
import io.specmatic.core.pattern.JSONObjectPattern
import org.junit.jupiter.api.Test
import application.VirtualServiceCommand.Companion.virtualServiceValidationRuleset
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.HttpResponsePattern
import io.specmatic.core.Scenario
import io.specmatic.core.buildHttpPathPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.pattern.StringPattern


class VirtualServiceCommandTest {
    @Test
    fun `returns errors for unsupported HTTP methods`() {
        val scenario: List<Scenario> = listOf(
            Scenario(
                name = "TRACE /resources",
                httpRequestPattern = HttpRequestPattern(
                    method = "TRACE",
                    httpPathPattern = buildHttpPathPattern("/resources")
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 200,
                    body = JSONObjectPattern(
                        mapOf(
                            "name" to StringPattern()
                        )
                    )
                ),
            )
        )
        val errors = virtualServiceValidationRuleset(scenario)
        assertEquals(1, errors.size)
        assertEquals("Invalid HTTP method TRACE in path /resources. Supported methods are: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS", errors[0])
    }

    @Test
    fun `returns errors when POST response does not contain id key`() {
        val scenarios: List<Scenario> = listOf(
            Scenario(
                name = "POST /resources",
                httpRequestPattern = HttpRequestPattern(
                    method = "POST",
                    httpPathPattern = buildHttpPathPattern("/resources")
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 200,
                    body = JSONObjectPattern(
                        mapOf(
                            "name" to StringPattern()
                        )
                    )
                ),
            )
        )
        val errors = virtualServiceValidationRuleset(scenarios)
        assertEquals(1, errors.size)
        assertEquals("Operation: POST /resources -> 200, must contain 'id' key in the response for POST requests", errors[0])
    }

    @Test
    fun `returns no errors for valid scenarios`() {
        val scenario: List<Scenario> = listOf(
            Scenario(
                name = "GET /resource",
                httpRequestPattern = HttpRequestPattern(
                    method = "GET",
                    httpPathPattern = buildHttpPathPattern("/resource")
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 200,
                    body = JSONObjectPattern(
                        mapOf(
                            "id" to NumberPattern(),
                            "name" to StringPattern()
                        )
                    )
                ),
            )
        )
        val errors = virtualServiceValidationRuleset(scenario)
        assertEquals(0, errors.size)
    }
}