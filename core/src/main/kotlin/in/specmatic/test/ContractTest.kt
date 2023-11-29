package `in`.specmatic.test

import `in`.specmatic.core.Result
import `in`.specmatic.core.TestResult

data class TestResultRecord(
    val path: String,
    val method: String,
    val responseStatus: Int,
    val result: TestResult,
    val sourceProvider: String? = null,
    val sourceRepository: String? = null,
    val sourceRepositoryBranch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null
) {
    val includeForCoverage = result !in listOf(TestResult.Skipped, TestResult.NotImplemented, TestResult.DidNotRun)
}

interface ContractTest {
    fun testResultRecord(result: Result): TestResultRecord
    fun generateTestScenarios(testVariables: Map<String, String>, testBaseURLs: Map<String, String>): List<ContractTest>
    fun testDescription(): String
    fun runTest(host: String?, port: String?, timeout: Int): Result
    fun runTest(testBaseURL: String?, timeOut: Int): Result
}
