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
    override fun testResultRecord(result: Result): TestResultRecord {
        val resultStatus = result.testResult()
        return TestResultRecord(convertPathParameterStyle(scenario.path), scenario.method, scenario.status, resultStatus, sourceProvider, sourceRepository, sourceRepositoryBranch, specification, serviceType)
    }

    override fun generateTestScenarios(
        testVariables: Map<String, String>,
        testBaseURLs: Map<String, String>
    ): List<ContractTest> {
        return scenario.generateContractTests(resolverStrategies, testVariables, testBaseURLs)
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeOut: Int): Result {
        val httpClient = HttpClient(testBaseURL, timeout = timeOut)
        return executeTest(scenario, httpClient, resolverStrategies).updateScenario(scenario)
    }

}
