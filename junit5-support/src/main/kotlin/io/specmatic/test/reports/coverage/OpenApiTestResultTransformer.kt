package io.specmatic.test.reports.coverage

import io.specmatic.conversions.SERVICE_TYPE_HTTP
import io.specmatic.core.TestResult
import io.specmatic.test.GroupedTestResultRecords
import io.specmatic.test.OpenApiTestResultRecord
import io.specmatic.test.groupTestResults
import io.specmatic.test.report.Remarks
import io.specmatic.test.report.ResultStatistics
import io.specmatic.test.report.interfaces.TestResultTransformer
import io.specmatic.test.sortTestResults
import kotlin.math.roundToInt


class OpenApiTestResultTransformer(private val testResultOutput: OpenApiTestResultOutput): TestResultTransformer {

    override fun toReportInput(): OpenApiReportInput {
        val allTestResults = transformTestResults(testResultOutput.testResultRecords)

        val coverageRows = createCoverageRows(allTestResults.groupTestResults())
        val statistics = createStatistics(allTestResults)

        return OpenApiReportInput(
            configFilePath = testResultOutput.configFilePath,
            coverageRows = coverageRows,
            testResultRecords = allTestResults,
            statistics = statistics,
            testsStartTime = testResultOutput.testStartTime,
            testsEndTime = testResultOutput.testEndTime,
            actuatorEnabled = testResultOutput.isActuatorEnabled()
        )
    }

    private fun transformTestResults(testResults: List<OpenApiTestResultRecord>): List<OpenApiTestResultRecord> {
        val missingFromSpecResults = createMissingTestResults()
        val notGeneratedResults = createNotGeneratedResults()

        val nonExcludedTestResults = testResults.filterNot { testResultOutput.isPathExcluded(it.path) }
        val allTestResults = nonExcludedTestResults.plus(missingFromSpecResults).plus(notGeneratedResults)

        return allTestResults.identifyAndUpdateUnimplementedTests().sortTestResults()
    }

    private fun createCoverageRows(groupedTestResults: GroupedTestResultRecords): List<OpenApiCoverageRow> {
        val coverageRows = mutableListOf<OpenApiCoverageRow>()

        groupedTestResults.forEach { (route, methodMap) ->
            val topLevelCoverageRow = createTopLevelApiCoverageRow(route, methodMap)
            val routeAPIRows = mutableListOf<OpenApiCoverageRow>()

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
                                exercisedCount = testResults.count { it.isExercised },
                                remark = Remarks.resolve(testResults)
                            )
                        )
                    }
                }
            }

            coverageRows.addAll(routeAPIRows)
        }

        return coverageRows
    }

    private fun createStatistics(testResults: List<OpenApiTestResultRecord>): ResultStatistics {
        val testsGroupedByPath = testResults.groupBy { it.path }

        val missedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.all { it.testResult == TestResult.MissingInSpec }
        }

        val notImplementedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.all { it.testResult == TestResult.NotImplemented }
        }

        val partiallyMissedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.any { it.testResult == TestResult.MissingInSpec } && tests.any { it.testResult != TestResult.MissingInSpec }
        }

        val partiallyNotImplementedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.any { it.testResult == TestResult.NotImplemented } && tests.any { it.testResult != TestResult.NotImplemented }
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

    private fun List<OpenApiTestResultRecord>.identifyAndUpdateUnimplementedTests(): List<OpenApiTestResultRecord> {
        val failedTestResults = this.filter(::isTestResultFailed)
        val missingTestResults = createMissingResultsFromFailed(failedTestResults)

        if (testResultOutput.isActuatorEnabled()) {
            val failedAndUnimplementedResults = updateUnImplementedTestResultsFromFailed(failedTestResults)
            return this.minus(failedTestResults.toSet()).plus(missingTestResults).plus(failedAndUnimplementedResults)
        }

        return this.plus(missingTestResults)
    }

    private fun createMissingTestResults(): List<OpenApiTestResultRecord> {
        val applicationApis = testResultOutput.applicationAPIs
        val nonExcludedApplicationApis = applicationApis.filterNot { testResultOutput.isPathExcluded(it.path) }
        val missingApis = nonExcludedApplicationApis.filterNot { testResultOutput.isPathAndMethodInEndpoints(it.path, it.method) }

        return missingApis.map {
            OpenApiTestResultRecord(
                path = it.path, method = it.method, responseStatus = 0,
                testResult = TestResult.MissingInSpec,
                serviceType = SERVICE_TYPE_HTTP
            )
        }
    }

    private fun createNotGeneratedResults(): List<OpenApiTestResultRecord> {
        val endpoints = testResultOutput.allEndpoints
        val nonExcludedEndpoints = endpoints.filterNot { testResultOutput.isPathExcluded(it.path) }
        val notGeneratedEndpoints = nonExcludedEndpoints.filterNot {
            testResultOutput.isPathMethodAndResponseInTestResults(it.path, it.method, it.responseStatus)
        }

        return notGeneratedEndpoints.map {
            OpenApiTestResultRecord(
                path = it.path, method = it.method, responseStatus = it.responseStatus,
                testResult = TestResult.NotCovered,
                sourceProvider = it.sourceProvider, sourceRepository = it.sourceRepository,
                sourceRepositoryBranch = it.sourceRepositoryBranch, specification = it.specification,
                serviceType = it.serviceType
            )
        }
    }

    private fun createMissingResultsFromFailed(failedTestResults: List<OpenApiTestResultRecord>): List<OpenApiTestResultRecord> {
        return failedTestResults.filter(::isTestResultMissingFromSpec).map {
            it.copy(
                responseStatus = it.actualResponseStatus,
                testResult = TestResult.MissingInSpec,
                actualResponseStatus = it.actualResponseStatus
            )
        }
    }

    private fun updateUnImplementedTestResultsFromFailed(failedTestResults: List<OpenApiTestResultRecord>): List<OpenApiTestResultRecord> {
        return failedTestResults.filter(::isTestResultUnImplemented).map {
            it.copy(testResult = TestResult.NotImplemented)
        }.plus(failedTestResults.filterNot(::isTestResultUnImplemented))
    }

    // Helper Methods

    private fun isTestResultFailed(testResultRecord: OpenApiTestResultRecord): Boolean {
        return testResultRecord.testResult == TestResult.Failed
    }

    private fun isTestResultUnImplemented(testResultRecord: OpenApiTestResultRecord): Boolean {
        return !testResultOutput.isPathAndMethodInApplicationsApis(testResultRecord.path, testResultRecord.method)
    }

    private fun isTestResultMissingFromSpec(testResultRecord: OpenApiTestResultRecord): Boolean {
        return !testResultRecord.isConnectionRefused() && !testResultOutput.isPathAndMethodInEndpoints(
            testResultRecord.path, testResultRecord.method, testResultRecord.actualResponseStatus
        )
    }

    private fun createTopLevelApiCoverageRow(path: String, methodMap: Map<String, Map<Int, List<OpenApiTestResultRecord>>>): OpenApiCoverageRow {
        val (method, responseStatus) = methodMap.entries.first().let { it.key to it.value.keys.first() }

        val testResults = methodMap[method]?.get(responseStatus) ?: emptyList()
        val exercisedCount = testResults.count { it.isExercised }
        val remarks = Remarks.resolve(testResults)

        val (totalMethodResponseCodeCount, totalMethodResponseCodeCoveredCount) = methodMap
            .flatMap { it.value.values }
            .let { results ->
                val count = results.sumOf { responseResults -> responseResults.size }
                val coveredCount = results.sumOf { responseResults -> responseResults.count { it.isCovered } }
                count to coveredCount
            }

        val coveragePercentage = (totalMethodResponseCodeCoveredCount.toFloat() / totalMethodResponseCodeCount.toFloat()) * 100

        return OpenApiCoverageRow(
            path = path,
            method = method,
            responseStatus = responseStatus,
            count  = exercisedCount,
            coveragePercentage = coveragePercentage.roundToInt(),
            remarks = remarks
        )
    }
}
