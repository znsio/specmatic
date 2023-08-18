package `in`.specmatic.test

import `in`.specmatic.core.Result
import `in`.specmatic.core.TestResult

data class TestResultRecord(val path: String, val method: String, val responseStatus: Int, val result: TestResult)

interface ContractTest {
    fun testResultRecord(result: Result): TestResultRecord
    fun generateTestScenarios(testVariables: Map<String, String>, testBaseURLs: Map<String, String>): List<ContractTest>
    fun testDescription(): String
    fun isBasedOnExample(): Boolean
    fun runTest(host: String?, port: String?, timeout: Int): Result
    fun runTest(testBaseURL: String?, timeOut: Int): Result
}
