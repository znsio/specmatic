package application

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import io.specmatic.core.Scenario
import io.specmatic.core.pattern.JSONObjectPattern
import org.junit.jupiter.api.Test
import application.VirtualServiceCommand.Companion.virtualServiceValidationRuleset


class VirtualServiceCommandTest {
    @Test
    fun `returns errors for unsupported HTTP methods`() {
        val scenario: List<Scenario> = listOf(
            mockk {
                every { httpRequestPattern.method } returns "TRACE"
                every { path } returns "/resource"
            }
        )
        val errors = virtualServiceValidationRuleset(scenario)
        assertEquals(1, errors.size)
        assertEquals("Invalid HTTP method TRACE in path /resource, The supported methods are GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS", errors[0])
    }

    @Test
    fun `returns errors when POST response does not contain id key`() {
        val scenario: List<Scenario> = listOf(
            mockk {
                every { path } returns "/resource"
                every { httpRequestPattern.method } returns "POST"
                every { isA2xxScenario() } returns true
                every { httpResponsePattern.body } returns mockk<JSONObjectPattern> {
                    every { pattern.keys } returns setOf("name")
                }
                every { apiDescription } returns "POST /resource"
            }
        )
        val errors = virtualServiceValidationRuleset(scenario)
        assertEquals(1, errors.size)
        assertEquals("Operation: POST /resource, does not contains <id> key in the response section as a required field", errors[0])
    }

    @Test
    fun `returns errors for nested resources in path`() {
        val scenario: List<Scenario> = listOf(
            mockk {
                every { path } returns "/resource/add"
                every { httpRequestPattern.method } returns "POST"
                every { isA2xxScenario() } returns true
                every { httpResponsePattern.body } returns mockk<JSONObjectPattern> {
                    every { pattern.keys } returns setOf("id")
                }
                every { apiDescription } returns "POST /resource/add"
            }
        )
        val errors = virtualServiceValidationRuleset(scenario)
        assertEquals(1, errors.size)
        assertEquals("Operation POST /resource/add, contains invalid nested resource add, Resources should follow flat structure like /resource or /resource/{id}", errors[0])
    }

    @Test
    fun `returns no errors for valid scenarios`() {
        val scenario: List<Scenario> = listOf(
            mockk {
                every { path } returns "/resource/(id:number)"
                every { httpRequestPattern.method } returns "GET"
                every { isA2xxScenario() } returns true
                every { apiDescription } returns "GET /resource/{id}"
            }
        )
        val errors = virtualServiceValidationRuleset(scenario)
        assertEquals(0, errors.size)
    }
}