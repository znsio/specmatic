package `in`.specmatic.test

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
    val isExercised = result !in listOf(TestResult.Skipped, TestResult.DidNotRun)
    val isCovered = result !in listOf(TestResult.Skipped, TestResult.DidNotRun, TestResult.NotImplemented)
}