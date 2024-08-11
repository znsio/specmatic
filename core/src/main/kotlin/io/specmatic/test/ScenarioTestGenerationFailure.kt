package io.specmatic.test

import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Scenario

class ScenarioTestGenerationFailure(val scenario: Scenario, val failure: Result.Failure): ContractTest {
    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord? {
        return null
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeoutInMilliseconds: Long): Pair<Result, HttpResponse?> {
        return Pair(failure.updateScenario(scenario), null)
    }

    override fun runTest(testExecutor: TestExecutor): Pair<Result, HttpResponse?> {
        return Pair(failure.updateScenario(scenario), null)
    }

    override fun plusValidator(validator: ResponseValidator): ContractTest {
        return this
    }

}