package io.specmatic.test.reports.coverage

import io.specmatic.core.log.CurrentDate
import io.specmatic.test.API
import io.specmatic.test.OpenApiTestResultRecord
import io.specmatic.test.report.interfaces.TestResultOutput

data class OpenApiTestResultOutput (
    override val configFilePath: String,
    override val testResultRecords: List<OpenApiTestResultRecord>,
    override val testStartTime: CurrentDate,
    override val testEndTime: CurrentDate,
    val applicationAPIs: List<API>,
    val excludedAPIs: List<String>,
    val allEndpoints: List<Endpoint>,
    val endpointsAPISet: Boolean,
): TestResultOutput<OpenApiTestResultRecord> {

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

