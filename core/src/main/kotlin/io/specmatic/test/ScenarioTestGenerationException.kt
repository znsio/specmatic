package io.specmatic.test

import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.exceptionCauseMessage

class ScenarioTestGenerationException(val scenario: Scenario, val e: Throwable, val message: String, val breadCrumb: String?) : ContractTest {
    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord? {
        return null
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeoutInMilliseconds: Long): Pair<Result, HttpResponse?> {
        return error()
    }

    override fun runTest(testExecutor: TestExecutor): Pair<Result, HttpResponse?> {
        return error()
    }

    override fun plusValidator(validator: ResponseValidator): ContractTest {
        return this
    }

    fun error(): Pair<Result, HttpResponse?> {
        val result: Result = when(e) {
            is ContractException -> Result.Failure(message, e.failure(), breadCrumb = breadCrumb ?: "").updateScenario(scenario)
            else -> Result.Failure(message + " - " + exceptionCauseMessage(e), breadCrumb = breadCrumb ?: "").updateScenario(scenario)
        }

        return Pair(result, null)
    }
}

