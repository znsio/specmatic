package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.Result
import `in`.specmatic.core.Scenario
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.exceptionCauseMessage

class ScenarioTestGenerationException(val scenario: Scenario, val e: Throwable, val message: String, val breadCrumb: String?) : ContractTest {
    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord? {
        return null
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeOut: Int): Pair<Result, HttpResponse?> {
        val result: Result = when(e) {
            is ContractException -> Result.Failure(message, e.failure(), breadCrumb = breadCrumb ?: "").updateScenario(scenario)
            else -> Result.Failure(message + " - " + exceptionCauseMessage(e), breadCrumb = breadCrumb ?: "").updateScenario(scenario)
        }

        return Pair(result, null)
    }
}

