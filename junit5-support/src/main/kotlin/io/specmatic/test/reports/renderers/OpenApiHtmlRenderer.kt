package io.specmatic.test.reports.renderers

import io.specmatic.core.ReportFormatterType
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.log.logger
import io.specmatic.test.TestInteractionsLog.displayName
import io.specmatic.test.TestInteractionsLog.duration
import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.OpenApiTestResultRecord
import io.specmatic.test.groupTestResults
import io.specmatic.test.report.interfaces.ReportInput
import io.specmatic.test.report.interfaces.ReportRenderer
import io.specmatic.test.reports.coverage.OpenApiReportInput
import io.specmatic.test.reports.coverage.groupCoverageRows
import io.specmatic.test.reports.coverage.html.*
import java.util.*

class OpenApiHtmlRenderer : ReportRenderer {

    companion object {
        private val tableColumns = listOf(
            TableColumn(name = "Path", colSpan = 2),
            TableColumn(name = "Method", colSpan = 1),
            TableColumn(name = "Status", colSpan = 1),
        )
        val actuatorEnabled = SpecmaticJUnitSupport.openApiCoverageReportInput.endpointsAPISet
    }

    override fun render(reportInput: ReportInput, specmaticConfig: SpecmaticConfig): String {
        reportInput as OpenApiReportInput

        logger.log("Generating HTML report...")
        val reportConfiguration = specmaticConfig.report!!
        val htmlReportConfiguration = reportConfiguration.formatters!!.first { it.type == ReportFormatterType.HTML }
        val openApiSuccessCriteria = reportConfiguration.types.apiCoverage.openAPI.successCriteria
        val (host, port) = reportInput.getHostAndPort()

        val reportData = HtmlReportData(
            totalCoveragePercentage = report.totalCoveragePercentage, tableRows = makeTableRows(report, htmlReportConfiguration),
            scenarioData = makeScenarioData(report), totalTestDuration = report.getTotalDuration()
            totalCoveragePercentage = reportInput.totalCoveragePercentage(), tableRows = makeTableRows(reportInput),
            scenarioData = makeScenarioData(reportInput), totalTestDuration = reportInput.getTotalDuration()
        )

        val sutInfo = SutInfo(
            host = host, port = port,
            mainGroupCount = reportInput.statistics.totalEndpointsCount, actuatorEnabled = reportInput.isActuatorEnabled()
        )

        val htmlReportInput = HtmlReportInput(
            reportFormat = htmlReportConfiguration, successCriteria = openApiSuccessCriteria,
            specmaticImplementation = "OpenAPI", specmaticVersion = getSpecmaticVersion(),
            tableColumns = tableColumns, reportData = reportData, specmaticConfig = specmaticConfig,
            sutInfo = sutInfo
        )

        val htmlFile = HtmlReport(htmlReportInput).generate()
        return "Successfully generated HTML report at file:///${htmlFile.toURI().path.replace("./", "")}"
    }

    private fun getSpecmaticVersion(): String {
        val props = Properties()
        OpenApiHtmlRenderer::class.java.classLoader.getResourceAsStream("version.properties").use {
            props.load(it)
        }
        return props.getProperty("version")
    }

    private fun makeTableRows(report: OpenAPICoverageConsoleReport, htmlReportConfiguration: ReportFormatter): List<TableRow> {
        val updatedCoverageRows = when(htmlReportConfiguration.lite) {
            true -> reCreateCoverageRowsForLite(report, report.coverageRows)
            else -> report.coverageRows
        }

        return report.getGroupedCoverageRows(updatedCoverageRows).flatMap { (_, methodGroup) ->
            methodGroup.flatMap { (_, statusGroup) ->
                statusGroup.flatMap { (_, coverageRows) ->
                    coverageRows.map {
                        TableRow(
                            coveragePercentage = it.coveragePercentage,
                            groups = listOf(
                                TableRowGroup(columnName = "Path", value = it.path, rowSpan = methodGroup.values.sumOf { rows -> rows.size }, showRow = it.showPath),
                                TableRowGroup(columnName = "Method", value = it.method, rowSpan = statusGroup.values.sumOf { rows -> rows.size }, showRow = it.showMethod),
                                TableRowGroup(columnName = "Status", value = it.responseStatus, rowSpan = 1, showRow = true)
                            ),
                            exercised = it.exercisedCount,
                            result = it.remark
                        )
                    }
                }
            }
        }
    }

    private fun getTotalDuration(report: OpenAPICoverageConsoleReport): Long {
        return report.httpLogMessages.sumOf { it.duration() }
    }

    private fun makeScenarioData(report: OpenApiReportInput): ScenarioDataGroup {
        val groupedTestResultRecords = report.testResultRecords.groupTestResults()
        val scenarioData = ScenarioDataGroup()

        for ((path, methodGroup) in groupedTestResultRecords) {
            scenarioData.subGroup[path] = ScenarioDataGroup(report.testResultRecords)
            for ((method, statusGroup) in methodGroup) {
                scenarioData.subGroup[path]!!.subGroup[method] = ScenarioDataGroup()
                for ((status, testResultRecords) in statusGroup) {
                    scenarioData.subGroup[path]!!.subGroup[method]!!.subGroup[status.toString()] = ScenarioDataGroup()
                    val scenarioDataList = testResultRecords.map { test ->
                        val matchingLogMessage = report.httpLogMessages.firstOrNull { it.scenario == test.scenarioResult?.scenario }
                        val scenarioName = getTestName(test, matchingLogMessage)
                        val (requestString, requestTime) = getRequestString(matchingLogMessage)
                        val (responseString, responseTime) = getResponseString(matchingLogMessage)

                        ScenarioData(
                            name = scenarioName,
                            baseUrl = getBaseUrl(matchingLogMessage),
                            duration = matchingLogMessage?.duration() ?: 0,
                            testResult = test.testResult,
                            valid = test.isValid,
                            wip = test.isWip,
                            request = requestString,
                            requestTime = requestTime,
                            response = responseString,
                            responseTime = responseTime,
                            specFileName = getSpecFileName(test, matchingLogMessage),
                            details = getReportDetail(test)
                        )
                    }
                    scenarioData.subGroup[path]!!.subGroup[method]!!.subGroup[status.toString()]!!.data = scenarioDataList
                }
            }
        }
        return scenarioData
    }

    private fun getTestName(testResult: OpenApiTestResultRecord, httpLogMessage: HttpLogMessage?): String {
        return httpLogMessage?.displayName() ?: "Scenario: ${testResult.path} -> ${testResult.responseStatus}"
    }

    private fun getBaseUrl(httpLogMessage: HttpLogMessage?): String {
        return httpLogMessage?.targetServer ?: "Unknown baseURL"
    }

    private fun getRequestString(httpLogMessage: HttpLogMessage?): Pair<String, Long> {
        return Pair(
            httpLogMessage?.request?.toLogString() ?: "No Request",
            httpLogMessage?.requestTime?.toEpochMillis() ?: 0
        )
    }

    private fun getResponseString(httpLogMessage: HttpLogMessage?): Pair<String, Long> {
        return Pair(
            httpLogMessage?.response?.toLogString() ?: "No Response",
            httpLogMessage?.responseTime?.toEpochMillis() ?: 0
        )
    }

    private fun getSpecFileName(testResult: OpenApiTestResultRecord, httpLogMessage: HttpLogMessage?): String {
        return testResult.specification ?: httpLogMessage?.scenario?.specification ?: "Unknown Spec File"
    }

    private fun getReportDetail(testResult: OpenApiTestResultRecord): String {
        return testResult.scenarioResult?.reportString() ?: ""
    }

    private fun reCreateCoverageRowsForLite(report: OpenAPICoverageConsoleReport, coverageRows: List<OpenApiCoverageConsoleRow>): List<OpenApiCoverageConsoleRow> {
        val exercisedRows = coverageRows.filter { it.count.toInt() > 0 }
        val updatedRows = mutableListOf<OpenApiCoverageConsoleRow>()

        report.getGroupedCoverageRows(exercisedRows).forEach { (_, methodGroup) ->
            val rowGroup = mutableListOf<OpenApiCoverageConsoleRow>()

            methodGroup.forEach { (method, statusGroup) ->
                statusGroup.forEach { (_, coverageRows) ->
                    coverageRows.forEach {
                        if (rowGroup.isEmpty()) {
                            rowGroup.add(it.copy(showPath = true, showMethod = true))
                        } else {
                            val methodExists = rowGroup.any {row ->  row.method == method }
                            rowGroup.add(it.copy(showPath = false, showMethod = !methodExists))
                        }
                    }
                }
            }

            updatedRows.addAll(rowGroup)
        }

        return updatedRows
    }
}