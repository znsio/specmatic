package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.HttpClient

data class Contract(val contract: Feature) {
    companion object {
        fun fromGherkin(contractGherkin: String): Contract {
            return Contract(parseGherkinStringToFeature(contractGherkin))
        }
    }

    fun samples(fake: HttpStub) = samples(fake.endPoint)

    private fun samples(endPoint: String) {
        val contractBehaviour = contract
        val httpClient = HttpClient(endPoint)

        contractBehaviour.generateContractTestScenarios(emptyList()).map { it.second.value }.fold(Results()) { results, scenario ->
            Results(results = results.results.plus(executeTest(scenario, httpClient)).toMutableList())
        }
    }
}
