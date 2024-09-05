package io.specmatic.test.reports.coverage

import io.specmatic.conversions.SERVICE_TYPE_HTTP
import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.TestResult
import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import kotlin.math.roundToInt

typealias GroupedTestResultRecords = Map<String, Map<String, Map<Int, List<TestResultRecord>>>>

class TestResultsTransformer(private val coverageReportInput: OpenApiCoverageReportInput) {

    fun toCoverageReport(): OpenAPICoverageConsoleReport {
        val allTestResults = transformTestResults(coverageReportInput.getTestResultRecords())

        val coverageRows = createCoverageRows(allTestResults.groupByPathMethodAndResponse())
        val statistics = createStatistics(allTestResults)

        return OpenAPICoverageConsoleReport(
            configFilePath = coverageReportInput.getConfigFilePath(),
            coverageRows = coverageRows,
            testResultRecords = allTestResults,
            statistics = statistics,
            testsStartTime = coverageReportInput.getTestStartTime(),
            testsEndTime = coverageReportInput.getTestEndTime()
        )
    }

    private fun transformTestResults(testResults: List<TestResultRecord>): List<TestResultRecord> {
        val missingFromSpecResults = createMissingTestResults()
        val notGeneratedResults = createNotGeneratedResults()

        val nonExcludedTestResults = testResults.filterNot { coverageReportInput.isPathExcluded(it.path) }
        val allTestResults = nonExcludedTestResults.plus(missingFromSpecResults).plus(notGeneratedResults)

        return allTestResults.identifyAndUpdateUnimplementedTests()
            .identifyAndUpdateWipTests()
            .identifyAndUpdateInvalidTests()
            .sortByPathMethodAndResponse()
    }

    private fun createCoverageRows(groupedTestResults: GroupedTestResultRecords): List<OpenApiCoverageConsoleRow> {
        val coverageRows = mutableListOf<OpenApiCoverageConsoleRow>()

        groupedTestResults.forEach { (route, methodMap) ->
            val topLevelCoverageRow = createTopLevelApiCoverageRow(route, methodMap)
            val routeAPIRows = mutableListOf<OpenApiCoverageConsoleRow>()

            methodMap.forEach { (method, responseCodeMap) ->
                responseCodeMap.forEach { (responseStatus, testResults) ->
                    if (routeAPIRows.isEmpty()) {
                        routeAPIRows.add(topLevelCoverageRow)
                    } else {
                        val showMethod = routeAPIRows.none { it.method == method }
                        routeAPIRows.add(
                            topLevelCoverageRow.copy(
                                method = method,
                                showMethod = showMethod,
                                showPath = false,
                                responseStatus = responseStatus.toString(),
                                count = testResults.count { it.isExercised }.toString(),
                                remarks = Remarks.resolve(testResults)
                            )
                        )
                    }
                }
            }

            coverageRows.addAll(routeAPIRows)
        }

        return coverageRows
    }

    private fun createStatistics(testResults: List<TestResultRecord>): ResultStatistics {
        val testsGroupedByPath = testResults.groupBy { it.path }

        val missedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.all { it.result == TestResult.MissingInSpec }
        }

        val notImplementedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.all { it.result == TestResult.NotImplemented }
        }

        val partiallyMissedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.any { it.result == TestResult.MissingInSpec } && tests.any { it.result != TestResult.MissingInSpec }
        }

        val partiallyNotImplementedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.any { it.result == TestResult.NotImplemented } && tests.any { it.result != TestResult.NotImplemented }
        }

        return ResultStatistics(
            totalEndpointsCount = testsGroupedByPath.size,
            missedEndpointsCount = missedAPICount,
            notImplementedAPICount = notImplementedAPICount,
            partiallyMissedEndpointsCount = partiallyMissedAPICount,
            partiallyNotImplementedAPICount = partiallyNotImplementedAPICount
        )
    }

    // TestResultRecord Updating / Creation Methods

    private fun List<TestResultRecord>.identifyAndUpdateWipTests(): List<TestResultRecord> {
        val wipTestResults = this.filter(::isTestResultWip)
        val updatedWipTestResults = wipTestResults.map { it.copy(isWip = true) }
        return this.minus(wipTestResults.toSet()).plus(updatedWipTestResults)
    }

    private fun List<TestResultRecord>.identifyAndUpdateInvalidTests(): List<TestResultRecord> {
        val invalidTestResults = this.filterNot(::isTestResultValid)
        val updatedInvalidTestResults = invalidTestResults.map { it.copy(isValid = false) }
        return this.minus(invalidTestResults.toSet()).plus(updatedInvalidTestResults)
    }

    private fun List<TestResultRecord>.identifyAndUpdateUnimplementedTests(): List<TestResultRecord> {
        val failedTestResults = this.filter(::isTestResultFailed)
        val missingTestResults = createMissingResultsFromFailed(failedTestResults)

        if (coverageReportInput.isActuatorEnabled()) {
            val failedAndUnimplementedResults = updateUnImplementedTestResultsFromFailed(failedTestResults)
            return this.minus(failedTestResults.toSet()).plus(missingTestResults).plus(failedAndUnimplementedResults)
        }

        return this.plus(missingTestResults)
    }

    private fun List<TestResultRecord>.sortByPathMethodAndResponse(): List<TestResultRecord> {
        return this.groupBy { "${convertPathParameterStyle(it.path)}-${it.method}-${it.responseStatus}" }
            .toSortedMap().values.flatten()
    }

    private fun List<TestResultRecord>.groupByPathMethodAndResponse(): GroupedTestResultRecords {
        return this.groupBy { it.path }.mapValues { pathGroup ->
            pathGroup.value.groupBy { it.method }.mapValues { methodGroup ->
                methodGroup.value.groupBy { it.responseStatus }
            }
        }
    }

    private fun createMissingTestResults(): List<TestResultRecord> {
        val applicationApis = coverageReportInput.getApplicationAPIs()
        val nonExcludedApplicationApis = applicationApis.filterNot { coverageReportInput.isPathExcluded(it.path) }
        val missingApis = nonExcludedApplicationApis.filterNot { coverageReportInput.isPathAndMethodInEndpoints(it.path, it.method) }

        return missingApis.map {
            TestResultRecord(
                path = it.path, method = it.method, responseStatus = 0,
                result = TestResult.MissingInSpec,
                serviceType = SERVICE_TYPE_HTTP
            )
        }
    }

    private fun createNotGeneratedResults(): List<TestResultRecord> {
        val endpoints = coverageReportInput.getEndpoints()
        val nonExcludedEndpoints = endpoints.filterNot { coverageReportInput.isPathExcluded(it.path) }
        val notGeneratedEndpoints = nonExcludedEndpoints.filterNot {
            coverageReportInput.isPathMethodAndResponseInTestResults(it.path, it.method, it.responseStatus)
        }

        return notGeneratedEndpoints.map {
            TestResultRecord(
                path = it.path, method = it.method, responseStatus = it.responseStatus,
                result = TestResult.NotCovered,
                sourceProvider = it.sourceProvider, sourceRepository = it.sourceRepository,
                sourceRepositoryBranch = it.sourceRepositoryBranch, specification = it.specification,
                serviceType = it.serviceType
            )
        }
    }

    private fun createMissingResultsFromFailed(failedTestResults: List<TestResultRecord>): List<TestResultRecord> {
        return failedTestResults.filter(::isTestResultMissingFromSpec).map {
            it.copy(
                responseStatus = it.actualResponseStatus,
                result = TestResult.MissingInSpec,
                actualResponseStatus = it.actualResponseStatus
            )
        }
    }

    private fun updateUnImplementedTestResultsFromFailed(failedTestResults: List<TestResultRecord>): List<TestResultRecord> {
        return failedTestResults.filter(::isTestResultUnImplemented).map {
            it.copy(result = TestResult.NotImplemented)
        }.plus(failedTestResults.filterNot(::isTestResultUnImplemented))
    }

    // Helper Methods

    private fun isTestResultFailed(testResultRecord: TestResultRecord): Boolean {
        return testResultRecord.result == TestResult.Failed
    }

    private fun isTestResultUnImplemented(testResultRecord: TestResultRecord): Boolean {
        return !coverageReportInput.isPathAndMethodInApplicationsApis(testResultRecord.path, testResultRecord.method)
    }

    private fun isTestResultMissingFromSpec(testResultRecord: TestResultRecord): Boolean {
        return !testResultRecord.isConnectionRefused() && !coverageReportInput.isPathAndMethodInEndpoints(
            testResultRecord.path, testResultRecord.method, testResultRecord.actualResponseStatus
        )
    }

    private fun isTestResultWip(testResultRecord: TestResultRecord): Boolean {
        return testResultRecord.scenarioResult?.scenario?.ignoreFailure == true
    }

    private fun isTestResultValid(testResultRecord: TestResultRecord): Boolean {
        val paramRegex = Regex("\\{.+}")
        val isPathWithParams = paramRegex.find(testResultRecord.path) != null
        if (isPathWithParams) return true

        return when (testResultRecord.responseStatus) {
            404 -> false
            else -> true
        }
    }

    private fun createTopLevelApiCoverageRow(path: String, methodMap: Map<String, Map<Int, List<TestResultRecord>>>): OpenApiCoverageConsoleRow {
        val (method, responseStatus) = methodMap.entries.first().let { it.key to it.value.keys.first() }

        val testResults = methodMap[method]?.get(responseStatus) ?: emptyList()
        val exercisedCount = testResults.count { it.isExercised }
        val remarks = Remarks.resolve(testResults)

        val (totalMethodResponseCodeCount, totalMethodResponseCodeCoveredCount) = methodMap
            .flatMap { it.value.values }
            .let { results ->
                val count = results.size
                val coveredCount = results.sumOf { responseResults -> responseResults.count { it.isCovered } }
                count to coveredCount
            }

        val coveragePercentage =
            ((totalMethodResponseCodeCoveredCount.toFloat() / totalMethodResponseCodeCount.toFloat()) * 100).roundToInt()

        return OpenApiCoverageConsoleRow(
            path = path,
            method = method,
            responseStatus = responseStatus,
            count  = exercisedCount,
            coveragePercentage = coveragePercentage,
            remarks = remarks
        )
    }
}
