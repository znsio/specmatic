package run.qontract.core

import run.qontract.core.pattern.Examples
import run.qontract.core.pattern.Row
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.value.True
import run.qontract.core.value.Value
import run.qontract.test.ContractTestException
import java.util.*
import kotlin.collections.HashMap

internal class ScenarioTest {
    @Test
    fun `should generate one test scenario when there are no examples`() {
        val scenario = Scenario("test", HttpRequestPattern(), HttpResponsePattern(), HashMap(), LinkedList(), HashMap(), HashMap())
        scenario.generateTestScenarios().let {
            assertThat(it.size).isEqualTo(1)
        }
    }

    @Test
    fun `should generate two test scenarios when there are two rows in examples`() {
        val patterns = Examples()
        patterns.rows.add(0, Row())
        patterns.rows.add(1, Row())
        val scenario = Scenario("test", HttpRequestPattern(), HttpResponsePattern(), HashMap(), listOf(patterns), HashMap(), HashMap())
        scenario.generateTestScenarios().let {
            assertThat(it.size).isEqualTo(2)
        }
    }

    @Test
    fun `should not match when there is an Exception`() {
        val httpResponsePattern = mockk<HttpResponsePattern>(relaxed = true)
        every { httpResponsePattern.matches(any(), any()) }.throws(ContractTestException("message"))
        val scenario = Scenario("test", HttpRequestPattern(), httpResponsePattern, HashMap(), LinkedList(), HashMap(), HashMap())
        scenario.matches(HttpResponse()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf(), listOf("Exception: message")))
        }
    }

    @Test
    fun `given a pattern in an example, facts declare without a value should pick up the pattern`() {
        val row = Row(listOf("id"), listOf("(string)"))

        val newState = newExpectedServerStateBasedOn(row, mapOf("id" to True), HashMap(), Resolver())

        assertThat(newState.getValue("id").toStringValue()).isNotEqualTo("(string)")
        assertThat(newState.getValue("id").toStringValue().trim().length).isGreaterThan(0)
    }

    @Test
    fun `given a pattern in an example in a scenario generated based on a row, facts declare without a value should pick up the pattern`() {
        val row = Row(listOf("id"), listOf("(string)"))
        val example = Examples(mutableListOf("id"))
        example.addRows(listOf(row))

        val state = HashMap(mapOf<String, Value>("id" to True))
        val scenario = Scenario("Test", HttpRequestPattern(urlMatcher = URLMatcher(emptyMap(), emptyList(), path="/")), HttpResponsePattern(status=200), state, listOf(example), HashMap(), HashMap())

        val testScenarios = scenario.generateTestScenarios()
        val newState = testScenarios.first().expectedFacts

        assertThat(newState.getValue("id").toStringValue()).isNotEqualTo("(string)")
        assertThat(newState.getValue("id").toStringValue().trim().length).isGreaterThan(0)
    }
}
