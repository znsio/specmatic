package io.specmatic.test.reports.renderers

import io.specmatic.core.ReportFormatterType
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.log.logger
import io.specmatic.test.TestInteractionsLog.displayName
import io.specmatic.test.TestInteractionsLog.duration
import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.html.*
import java.util.*

class CoverageReportHtmlRenderer : ReportRenderer<OpenAPICoverageConsoleReport> {

    companion object {
        private val tableColumns = listOf(
            TableColumn(name = "Path", colSpan = 2),
            TableColumn(name = "Method", colSpan = 1),
            TableColumn(name = "Status", colSpan = 1),
        )
        // TODO: Address this later
        val actuatorEnabled = false
    }

    override fun render(report: OpenAPICoverageConsoleReport, specmaticConfig: SpecmaticConfig): String {
        logger.log("Generating HTML report...")
        val reportConfiguration = specmaticConfig.report!!
        val htmlReportConfiguration = reportConfiguration.formatters!!.first { it.type == ReportFormatterType.HTML }
        val openApiSuccessCriteria = reportConfiguration.types.apiCoverage.openAPI.successCriteria
        val (host, port) = report.getHostAndPort()

        val reportData = HtmlReportData(
            totalCoveragePercentage = report.totalCoveragePercentage, tableRows = makeTableRows(report),
            scenarioData = makeScenarioData(report), totalTestDuration = report.getTotalDuration()
        )

        val htmlReportInformation = HtmlReportInformation(
            reportFormat = htmlReportConfiguration, successCriteria = openApiSuccessCriteria,
            specmaticImplementation = "OpenAPI", specmaticVersion = getSpecmaticVersion(),
            tableColumns = tableColumns, reportData = reportData, specmaticConfig = specmaticConfig,
            sutInfo = SutInfo(host = host, port = port, actuatorEnabled = actuatorEnabled, mainGroupCount = report.totalPaths)
        )

        val htmlFile = HtmlReport(htmlReportInformation).generate()
        return "Successfully generated HTML report at file:///${htmlFile.toURI().path.replace("./", "")}"
    }

    private fun getSpecmaticVersion(): String {
        val props = Properties()
        CoverageReportHtmlRenderer::class.java.classLoader.getResourceAsStream("version.properties").use {
            props.load(it)
        }
        return props.getProperty("version")
    }

    private fun makeTableRows(report: OpenAPICoverageConsoleReport): List<TableRow> {
        return report.getGroupedCoverageRows().flatMap { (_, methodGroup) ->
            methodGroup.flatMap { (_, statusGroup) ->
                statusGroup.flatMap { (_, coverageRows) ->
                    coverageRows.map {
                        TableRow(
                            coveragePercentage = it.coveragePercentage,
                            groups = listOf(
                                TableRowGroup(
                                    columnName = "Path",
                                    value = it.path,
                                    rowSpan = methodGroup.values.sumOf { rows -> rows.size },
                                    showRow = it.showPath
                                    ),
                                TableRowGroup(
                                    columnName = "Method",
                                    value = it.method,
                                    rowSpan = statusGroup.values.sumOf { rows -> rows.size },
                                    showRow = it.showMethod
                                ),
                                TableRowGroup(columnName = "Status", value = it.responseStatus, rowSpan = 1, showRow = true)
                            ),
                            exercised = it.count.toInt(),
                            result = it.remarks
                        )
                    }
                }
            }
        }
    }

    private fun makeScenarioData(report: OpenAPICoverageConsoleReport): ScenarioDataGroup {
        val groupedTestResultRecords = report.getGroupedTestResultRecords()
        val scenarioData = ScenarioDataGroup()

        for ((path, methodGroup) in groupedTestResultRecords) {
            scenarioData.subGroup[path] = ScenarioDataGroup()
            for ((method, statusGroup) in methodGroup) {
                scenarioData.subGroup[path]!!.subGroup[method] = ScenarioDataGroup()
                for ((status, testResultRecords) in statusGroup) {
                    scenarioData.subGroup[path]!!.subGroup[method]!!.subGroup[status] = ScenarioDataGroup()
                    val scenarioDataList = testResultRecords.map { test ->
                        val matchingLogMessage = report.httpLogMessages.firstOrNull { it.scenario == test.scenarioResult?.scenario }
                        val scenarioName = getTestName(test, matchingLogMessage)
                        val (requestString, requestTime) = getRequestString(matchingLogMessage)
                        val (responseString, responseTime) = getResponseString(matchingLogMessage)

                        ScenarioData(
                            name = scenarioName,
                            baseUrl = getBaseUrl(matchingLogMessage),
                            duration = matchingLogMessage?.duration() ?: 0,
                            testResult = test.result,
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
                    scenarioData.subGroup[path]!!.subGroup[method]!!.subGroup[status]!!.data = scenarioDataList
                }
            }
        }
        return scenarioData
    }

    private fun getTestName(testResult: TestResultRecord, httpLogMessage: HttpLogMessage?): String {
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

    private fun getSpecFileName(testResult: TestResultRecord, httpLogMessage: HttpLogMessage?): String {
        return testResult.specification ?: httpLogMessage?.scenario?.specification ?: "Unknown Spec File"
    }

    private fun getReportDetail(testResult: TestResultRecord): String {
        return testResult.scenarioResult?.reportString() ?: ""
    }
}