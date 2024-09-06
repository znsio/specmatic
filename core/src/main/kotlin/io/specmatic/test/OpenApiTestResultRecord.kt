package io.specmatic.test

import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.Result
import io.specmatic.core.TestResult
import io.specmatic.test.report.interfaces.TestResultRecord

typealias GroupedTestResultRecords = Map<String, Map<String, Map<Int, List<OpenApiTestResultRecord>>>>

data class OpenApiTestResultRecord (
    val path: String,
    val method: String,
    val responseStatus: Int,
    override val testResult: TestResult,
    val sourceProvider: String? = null,
    val sourceRepository: String? = null,
    val sourceRepositoryBranch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null,
    val actualResponseStatus: Int = 0,
    val scenarioResult: Result? = null,
): TestResultRecord {
    override val isValid = isTestResultValid(this)
    override val isWip: Boolean = scenarioResult?.scenario?.ignoreFailure == true
    override val isExercised = testResult !in setOf(TestResult.MissingInSpec, TestResult.NotCovered)
    override val isCovered = testResult !in setOf(TestResult.MissingInSpec, TestResult.NotCovered)

    fun isConnectionRefused() = actualResponseStatus == 0

    private fun isTestResultValid(testResultRecord: OpenApiTestResultRecord): Boolean {
        val paramRegex = Regex("\\{.+}")
        val isPathWithParams = paramRegex.find(testResultRecord.path) != null
        if (isPathWithParams) return true

        return when (testResultRecord.responseStatus) {
            404 -> false
            else -> true
        }
    }
}

fun List<OpenApiTestResultRecord>.sortTestResults(): List<OpenApiTestResultRecord> {
    return this.groupBy { "${convertPathParameterStyle(it.path)}-${it.method}-${it.responseStatus}" }
        .toSortedMap().values.flatten()
}

fun List<OpenApiTestResultRecord>.groupTestResults(): GroupedTestResultRecords {
    return this.groupBy { it.path }.mapValues { pathGroup ->
        pathGroup.value.groupBy { it.method }.mapValues { methodGroup ->
            methodGroup.value.groupBy { it.responseStatus }
        }
    }
}

