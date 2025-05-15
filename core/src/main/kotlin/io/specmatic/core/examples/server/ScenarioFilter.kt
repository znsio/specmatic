package io.specmatic.core.examples.server

import io.specmatic.core.Feature
import io.specmatic.core.filters.ScenarioMetadataFilter
import io.specmatic.core.filters.ScenarioMetadataFilter.Companion.filterUsing

class ScenarioFilter(filterName: String = "", filterNotName: String = "", filterClauses: String = "") {
    private val filter = filterClauses

    private val filterNameTokens = if(filterName.isNotBlank()) {
        filterName.trim().split(",").map { it.trim() }
    } else emptyList()

    private val filterNotNameTokens = if(filterNotName.isNotBlank()) {
        filterNotName.trim().split(",").map { it.trim() }
    } else emptyList()

    fun filter(feature: Feature): Feature {
        val scenariosFilteredByOlderSyntax = feature.scenarios.filter { scenario ->
            if(filterNameTokens.isNotEmpty()) {
                filterNameTokens.any { name -> scenario.testDescription().contains(name) }
            } else true
        }.filter { scenario ->
            if(filterNotNameTokens.isNotEmpty()) {
                filterNotNameTokens.none { name -> scenario.testDescription().contains(name) }
            } else true
        }

        val scenarioFilter = ScenarioMetadataFilter.from(filter)

        val filteredScenarios = filterUsing(scenariosFilteredByOlderSyntax.asSequence(), scenarioFilter).toList()


        return feature.copy(scenarios = filteredScenarios)
    }
}

