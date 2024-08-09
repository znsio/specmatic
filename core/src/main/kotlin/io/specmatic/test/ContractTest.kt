package io.specmatic.test

import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Scenario

interface ResponseValidator {
    fun validate(scenario: Scenario, httpResponse: HttpResponse): Result?
}

interface ContractTest {
    fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord?
    fun testDescription(): String
    fun runTest(testBaseURL: String, timeoutInMilliseconds: Long): Pair<Result, HttpResponse?>
    fun runTest(testExecutor: TestExecutor): Pair<Result, HttpResponse?>

    fun plusValidator(validator: ResponseValidator): ContractTest
}
