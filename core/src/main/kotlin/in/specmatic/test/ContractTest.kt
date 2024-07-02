package `in`.specmatic.test

import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.Result
import `in`.specmatic.core.Scenario

interface ResponseValidator {
    fun validate(scenario: Scenario, httpResponse: HttpResponse): Result?
}

interface ContractTest {
    fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord?
    fun testDescription(): String
    fun runTest(testBaseURL: String, timeOut: Int): Pair<Result, HttpResponse?>
    fun runTest(testExecutor: TestExecutor): Pair<Result, HttpResponse?>

    fun plusValidator(validator: ResponseValidator): ContractTest
}
