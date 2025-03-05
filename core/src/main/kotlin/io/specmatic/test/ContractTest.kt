package io.specmatic.test

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.filters.ScenarioMetadata

interface ResponseValidator {
    fun validate(scenario: Scenario, httpResponse: HttpResponse): Result? {
        return null
    }

    fun postValidate(scenario: Scenario, httpRequest: HttpRequest, httpResponse: HttpResponse): Result? {
        return null
    }
}

interface ContractTest {
    fun toScenarioMetadata(): ScenarioMetadata
    fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord?
    fun testDescription(): String
    fun runTest(timeoutInMilliseconds: Long): Pair<Result, HttpResponse?>
    fun runTest(testExecutor: TestExecutor): Pair<Result, HttpResponse?>

    fun plusValidator(validator: ResponseValidator): ContractTest

    fun getBaseURL(): String
}
