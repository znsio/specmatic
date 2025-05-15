package io.specmatic.core

data class ScenarioStoreData(
    val scenario: Scenario,
    val originalOrder: Int,
    val pathGeneralityOrder: Int = scenario.httpRequestPattern.pathGenerality,
    val segmentsCountOrder: Int = scenario.httpRequestPattern.httpPathPattern?.pathSegmentPatterns?.size ?: 0
)

data class ScenarioStore(private val scenarioData: List<ScenarioStoreData>) {

    private val sortedData = scenarioData.sortedWith(compareBy({ it.pathGeneralityOrder }, { it.segmentsCountOrder }))

    val scenarios = sortedData.map { it.scenario }

    val scenariosWithOriginalOrder = scenarioData.sortedBy { it.originalOrder }.map { it.scenario }

    fun filter(predicate: (Scenario) -> Boolean): ScenarioStore {
        val filtered = scenarioData.filter { predicate(it.scenario) }
        return ScenarioStore(filtered)
    }

    fun <T> fold(initial: T, block: (T, Scenario) -> Pair<T, Scenario>): Pair<T, ScenarioStore> {
        val foldInitial = initial to emptyList<ScenarioStoreData>()

        val (finalAcc, updatedList) = sortedData.fold(foldInitial) { (acc, newList), data ->
            val (newAcc, newScenario) = block(acc, data.scenario)
            val updatedData = ScenarioStoreData(newScenario, data.originalOrder)
            newAcc to (newList + updatedData)
        }

        return finalAcc to ScenarioStore(updatedList)
    }

    companion object {
        fun empty(): ScenarioStore {
            return ScenarioStore(emptyList())
        }

        fun from(scenarios: List<Scenario>): ScenarioStore {
            val processedScenarios = scenarios.mapIndexed { index, scenario -> ScenarioStoreData(scenario, index) }
            return ScenarioStore(processedScenarios)
        }
    }
}
