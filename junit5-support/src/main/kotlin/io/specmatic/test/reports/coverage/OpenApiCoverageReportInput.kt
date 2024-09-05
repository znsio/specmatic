package io.specmatic.test.reports.coverage

import io.specmatic.core.log.CurrentDate
import io.specmatic.test.API
import io.specmatic.test.TestResultRecord
data class OpenApiCoverageReportInput(
    private val configFilePath: String,
    private val testResultRecords: List<TestResultRecord>,
    private val applicationAPIs: List<API>,
    private val excludedAPIs: List<String>,
    private val allEndpoints: List<Endpoint>,
    private val endpointsAPISet: Boolean,
    private val testStartTime: CurrentDate,
    private val testEndTime: CurrentDate = CurrentDate()
) {
    fun getConfigFilePath(): String {
        return configFilePath
    }

    fun getTestResultRecords(): List<TestResultRecord> {
        return testResultRecords
    }

    fun getApplicationAPIs(): List<API> {
        return applicationAPIs
    }

    fun getEndpoints(): List<Endpoint> {
        return allEndpoints
    }

    fun getTestStartTime(): CurrentDate {
        return testStartTime
    }

    fun getTestEndTime(): CurrentDate {
        return testEndTime
    }

    fun isPathAndMethodInEndpoints(path: String, method: String, response: Int? = null): Boolean {
        return allEndpoints.any { it.path == path && it.method == method && (response == null || it.responseStatus == response) }
    }

    fun isPathAndMethodInApplicationsApis(path: String, method: String): Boolean {
        return endpointsAPISet && applicationAPIs.any { it.path == path && it.method == method }
    }

    fun isPathMethodAndResponseInTestResults(path: String, method: String, response: Int): Boolean {
        return testResultRecords.any { it.path == path && it.method == method && it.responseStatus == response }
    }

    fun isPathExcluded(path: String): Boolean {
        return excludedAPIs.any { it == path }
    }

    fun isActuatorEnabled(): Boolean {
        return endpointsAPISet
    }
}

data class CoverageGroupKey(
    val sourceProvider: String?,
    val sourceRepository: String?,
    val sourceRepositoryBranch: String?,
    val specification: String?,
    val serviceType: String?
)

data class Endpoint(
    val path: String,
    val method: String,
    val responseStatus: Int,
    val sourceProvider: String? = null,
    val sourceRepository: String? = null,
    val sourceRepositoryBranch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null
)

data class ResultStatistics(
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int,
    val partiallyMissedEndpointsCount: Int,
    val notImplementedAPICount: Int,
    val partiallyNotImplementedAPICount: Int
)