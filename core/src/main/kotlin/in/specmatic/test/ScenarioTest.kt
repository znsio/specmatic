package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.*

class ScenarioTest(
    val scenario: Scenario,
    private val resolverStrategies: ResolverStrategies,
    private val sourceProvider: String? = null,
    private val sourceRepository: String? = null,
    private val sourceRepositoryBranch: String? = null,
    private val specification: String? = null,
    private val serviceType: String? = null,
) : ContractTest {
    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord {
        val resultStatus = result.testResult()

        val responseStatus = scenario.getStatus(response)
        return TestResultRecord(convertPathParameterStyle(scenario.path), scenario.method,
            responseStatus, resultStatus, sourceProvider, sourceRepository, sourceRepositoryBranch, specification, serviceType)
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeOut: Int): Pair<Result, HttpResponse?> {
        val httpClient = HttpClient(testBaseURL, timeout = timeOut)
        val (result, response) = executeTestAndReturnResultAndResponse(scenario, httpClient, resolverStrategies)
        return Pair(result.updateScenario(scenario), response)
    }

}
