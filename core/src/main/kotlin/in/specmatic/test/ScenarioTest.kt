package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.*
import `in`.specmatic.core.log.logger

class ScenarioTest(
    val scenario: Scenario,
    private val flagsBased: FlagsBased,
    private val sourceProvider: String? = null,
    private val sourceRepository: String? = null,
    private val sourceRepositoryBranch: String? = null,
    private val specification: String? = null,
    private val serviceType: String? = null,
    private val comment: String? = null
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
        if(comment != null) {
            logger.log(comment)
        }

        val httpClient = HttpClient(testBaseURL, timeout = timeOut)
        val (result, response) = executeTestAndReturnResultAndResponse(scenario, httpClient, flagsBased)
        return Pair(result.updateScenario(scenario), response)
    }

}
