package io.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScenarioStoreTest  {

    companion object {
        private fun scenarioFrom(path: String, method: String = "GET"): Scenario {
            return Scenario(ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(httpPathPattern = HttpPathPattern.from(path), method = method),
                httpResponsePattern = HttpResponsePattern(status = 200)
            ))
        }
    }

    @Test
    fun `should provide scenarios with sorted order by path generality and segments size`() {
        val scenarios = listOf(
            scenarioFrom("/test"),
            scenarioFrom("/test/latest"),
            scenarioFrom("/test/latest/reports"),
            scenarioFrom("/test/latest/reports/latest"),
            scenarioFrom("/test/(id:string)"),
            scenarioFrom("/test/(id:string)/reports"),
            scenarioFrom("/test/(id:string)/reports/(id:string)")
        )
        val scenarioStore = ScenarioStore.from(scenarios.shuffled())

        assertThat(scenarioStore.scenarios).containsExactlyElementsOf(scenarios)
    }

    @Test
    fun `should be able to re-create the original order on-demand`() {
        val scenarios = listOf(
            scenarioFrom("/test"),
            scenarioFrom("/test/(id:string)"),
            scenarioFrom("/test/latest"),
            scenarioFrom("/test/(id:string)/reports"),
            scenarioFrom("/test/latest/reports"),
            scenarioFrom("/test/(id:string)/reports/(id:string)"),
            scenarioFrom("/test/latest/reports/latest"),
        )
        val scenarioStore = ScenarioStore.from(scenarios)

        assertThat(scenarioStore.scenariosWithOriginalOrder).containsExactlyElementsOf(scenarios)
    }

    @Test
    fun `should retain the order information when folding`() {
        val scenarios = listOf(
            scenarioFrom("/test/(id:string)"),
            scenarioFrom("/test/latest"),
            scenarioFrom("/test/(id:string)/reports"),
            scenarioFrom("/test/latest/reports"),
        )
        val scenarioStore = ScenarioStore.from(scenarios)
        val (_, newStore) = scenarioStore.fold(emptyList<Nothing>()) { acc, scenario ->
            acc to scenario
        }

        assertThat(newStore.scenariosWithOriginalOrder).containsExactlyElementsOf(scenarios)
        assertThat(newStore.scenarios).containsExactlyElementsOf(listOf(
            scenarioFrom("/test/latest"),
            scenarioFrom("/test/latest/reports"),
            scenarioFrom("/test/(id:string)"),
            scenarioFrom("/test/(id:string)/reports"),
        ))
    }

    @Test
    fun `should retain the order information when filtering the scenarios`() {
        val scenarios = listOf(
            scenarioFrom("/test/(id:string)"),
            scenarioFrom("/test/latest"),
            scenarioFrom("/test/(id:string)/reports"),
            scenarioFrom("/test/latest/reports"),
        )
        val scenarioStore = ScenarioStore.from(scenarios)
        val newStore = scenarioStore.filter { it.path != "/test/(id:string)" }

        assertThat(newStore.scenariosWithOriginalOrder).containsExactlyElementsOf(listOf(
            scenarioFrom("/test/latest"),
            scenarioFrom("/test/(id:string)/reports"),
            scenarioFrom("/test/latest/reports"),
        ))
        assertThat(newStore.scenarios).containsExactlyElementsOf(listOf(
            scenarioFrom("/test/latest"),
            scenarioFrom("/test/latest/reports"),
            scenarioFrom("/test/(id:string)/reports"),
        ))
    }
}