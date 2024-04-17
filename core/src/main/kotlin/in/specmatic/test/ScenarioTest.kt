package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.*
import `in`.specmatic.core.log.HttpLogMessage
import `in`.specmatic.core.log.LogMessage
import `in`.specmatic.core.log.logger

class ScenarioTest(
    val scenario: Scenario,
    private val flagsBased: FlagsBased,
    private val sourceProvider: String? = null,
    private val sourceRepository: String? = null,
    private val sourceRepositoryBranch: String? = null,
    private val specification: String? = null,
    private val serviceType: String? = null,
    private val annotations: String? = null
) : ContractTest {
    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord {
        val resultStatus = result.testResult()

        val responseStatus = scenario.getStatus(response)
        return TestResultRecord(
            convertPathParameterStyle(scenario.path),
            scenario.method,
            responseStatus,
            resultStatus,
            sourceProvider,
            sourceRepository,
            sourceRepositoryBranch,
            specification,
            serviceType
        )
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeOut: Int): Pair<Result, HttpResponse?> {
        val log: (LogMessage) -> Unit = { logMessage ->
            logger.log(logMessage.withComment(this.annotations))
        }

        val httpClient = HttpClient(testBaseURL, log = log, timeout = timeOut)
        val (result, response) = executeTestAndReturnResultAndResponse(scenario, httpClient, flagsBased)
        return Pair(result.updateScenario(scenario), response)
    }

    private fun logComment() {
        if (annotations != null) {
            logger.log(annotations)
        }
    }

}

private fun LogMessage.withComment(comment: String?): LogMessage {
    return if (this is HttpLogMessage) {
        this.copy(comment = comment)
    } else {
        this
    }
}
