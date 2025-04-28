package application

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import io.specmatic.core.Scenario
import io.specmatic.core.pattern.JSONObjectPattern
import org.junit.jupiter.api.Test
import application.VirtualServiceCommand.Companion.virtualServiceValidationRuleset
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.HttpResponsePattern
import io.specmatic.core.buildHttpPathPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.pattern.StringPattern


class VirtualServiceCommandTest {
    @Test
    fun `returns errors for unsupported HTTP methods`() {
        val scenario: List<Scenario> = listOf(
            mockk {
                every { httpRequestPattern.method } returns "TRACE"
                every { path } returns "/resources"
            }
        )
        val errors = virtualServiceValidationRuleset(scenario)
        assertEquals(1, errors.size)
        assertEquals("Invalid HTTP method TRACE in path /resources, The supported methods are GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS", errors[0])
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
        assertEquals("Operation: POST /resources -> 200, does not contains <id> key in the response section as a required field", errors[0])
    }

    @Test
    fun `returns errors for nested resources in path`() {
        val scenario: List<Scenario> = listOf(
            Scenario(
                name = "POST /resources/add",
                httpRequestPattern = HttpRequestPattern(
                    method = "POST",
                    httpPathPattern = buildHttpPathPattern("/resources/add")
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
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
        assertEquals(1, errors.size)
        assertEquals("Operation POST /resources/add -> 201, contains invalid nested resource add, Resources should follow flat structure like /resource or /resource/{id}", errors[0])
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