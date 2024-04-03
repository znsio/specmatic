package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.Result
import `in`.specmatic.core.Scenario
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.exceptionCauseMessage

class ScenarioTestGenerationException(val scenario: Scenario, val e: Throwable, val message: String, val breadCrumb: String?) : ContractTest {
    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord {
        val failureResult = if(e is ContractException) {
            Result.Failure(message, e.failure(), breadCrumb ?: "")
        } else {
            val exceptionFailure = Result.Failure(exceptionCauseMessage(e), breadCrumb = breadCrumb ?: "")
            Result.Failure(message, exceptionFailure)
        }

        return TestResultRecord(convertPathParameterStyle(scenario.path), scenario.method, scenario.httpResponsePattern.status, failureResult.testResult())
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeOut: Int): Pair<Result, HttpResponse?> {
        return Pair(Result.Failure(exceptionCauseMessage(e)).updateScenario(scenario), null)
    }
}

