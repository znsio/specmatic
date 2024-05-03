package `in`.specmatic.test

import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.Result

interface ContractTest {
    fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord?
    fun testDescription(): String
    fun runTest(testBaseURL: String, timeOut: Int): Pair<Result, HttpResponse?>
}
