package `in`.specmatic.test

import `in`.specmatic.core.Result
import `in`.specmatic.core.TestResult

data class TestResultRecord(
    val path: String,
    val method: String,
    val responseStatus: Int,
    val result: TestResult,
    val sourceProvider: String = "",
    val sourceRepository: String = "",
    val sourceRepositoryBranch: String = "",
    val specification: String = "",
    val serviceType: String = ""
) {
    val includeForCoverage = result !in listOf(TestResult.Skipped, TestResult.NotImplemented)
}

interface ContractTest {
    fun testResultRecord(result: Result): TestResultRecord
    fun generateTestScenarios(testVariables: Map<String, String>, testBaseURLs: Map<String, String>): List<ContractTest>
    fun testDescription(): String
    fun runTest(host: String?, port: String?, timeout: Int): Result
    fun runTest(testBaseURL: String?, timeOut: Int): Result
}
