package io.specmatic.test.reports.coverage.html

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.specmatic.core.*
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.log.logger
import io.specmatic.test.SpecmaticJUnitSupport
import io.specmatic.test.TestInteractionsLog
import io.specmatic.test.TestInteractionsLog.displayName
import io.specmatic.test.TestInteractionsLog.duration
import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import io.specmatic.test.reports.coverage.html.HtmlTemplateConfiguration.Companion.configureTemplateEngine
import org.thymeleaf.context.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt

class HtmlReport(htmlReportFormat: ReportFormatter){

    private val groupedHttpLogMessages = TestInteractionsLog.testHttpLogMessages.groupBy { it.scenario?.method }
    private val groupedTestResultRecords = SpecmaticJUnitSupport.openApiCoverageReportInput.groupedTestResultRecords
    private val groupedApiCoverageRows = SpecmaticJUnitSupport.openApiCoverageReportInput.apiCoverageRows
        .groupBy { it.path }.mapValues { pathGroup -> pathGroup.value.groupBy { it.method } }
    private val groupedScenarios = groupScenarios()
    private val tableRows = tableRows()


    private val outputDirectory = htmlReportFormat.outputDirectory
    private val pageTitle = htmlReportFormat.title
    private val reportHeading = htmlReportFormat.heading

    private var totalTests = 0
    private var totalErrors = 0
    private var totalFailures = 0
    private var totalSkipped = 0
    private var totalSuccess = 0

    fun generate() {
        logger.log("Generating HTML report...")
        createAssetsDir(outputDirectory)
        calculateTestGroupCounts()

        val outFile = File(outputDirectory, "index.html")
        val htmlText = generateHtmlReportText()
        if (!outFile.parentFile.exists()) outFile.mkdirs()
        outFile.writer().use { it.write(htmlText) }
    }

    private fun generateHtmlReportText(): String {
        val templateVariables = mapOf(
            "pageTitle" to pageTitle,
            "reportHeading" to reportHeading,
            "totalCoverage" to totalCoverage(),
            "actuatorEnabled" to SpecmaticJUnitSupport.openApiCoverageReportInput.endpointsAPISet,
            "totalSuccess" to totalSuccess,
            "totalFailures" to totalFailures,
            "totalErrors" to totalErrors,
            "totalSkipped" to totalSkipped,
            "totalTests" to totalTests,
            "totalDuration" to getTotalDuration(),
            "generatedOn" to generatedOnTimestamp(),
            "tableRows" to tableRows,
            "specmaticVersion" to "[${getSpecmaticVersion()}]",
            "summaryResult" to summaryResult(),
            "jsonTestData" to dumpTestData(groupedScenarios)
        )

        return configureTemplateEngine().process(
            "report",
            Context().apply { setVariables(templateVariables) }
        )
    }

    private fun totalCoverage(): Int {
        if (totalTests == 0) return 0

        val rows = SpecmaticJUnitSupport.openApiCoverageReportInput.apiCoverageRows
        val totalCount = rows.count()
        val totalCoveredCount = rows.count { it.remarks == Remarks.Covered }
        return ((totalCoveredCount * 100) / totalCount).toDouble().roundToInt()
    }

    private fun summaryResult(): String {
        return if (totalFailures > 0 || totalErrors > 0) "rejected" else "approved"
    }

    private fun tableRows(): List<TableRow> {
        return groupedApiCoverageRows.flatMap { (_, methodGroup) ->
            methodGroup.flatMap { (_, coverageRows) ->
                coverageRows.map {
                    TableRow(
                        pathRowSpan = methodGroup.values.sumOf { rows ->  rows.size },
                        methodRowSpan = coverageRows.size,
                        showPath = it.showPath,
                        showMethod = it.showMethod,
                        coverageRow = it,
                        badgeColor = getBadgeColor(it)
                    )
                }
            }
        }
    }

    private fun calculateTestGroupCounts() {
        groupedTestResultRecords.forEach { pathGroup ->
            pathGroup.value.forEach { methodGroup ->
                methodGroup.value.forEach { responseGroup ->
                    responseGroup.value.forEach {
                        when (it.result) {
                            TestResult.NotCovered -> totalSkipped++
                            TestResult.Success  -> totalSuccess++
                            TestResult.Error -> if (it.isWip) totalSkipped++ else totalErrors++
                            else -> if(it.isWip) totalSkipped++ else totalFailures++
                        }
                        totalTests++
                    }
                }
            }
        }
    }

    private fun generatedOnTimestamp(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy h:mma", Locale.ENGLISH)
        return currentDateTime.format(formatter)
    }

    private fun getSpecmaticVersion(): String {
        val props = Properties()
        HtmlReport::class.java.classLoader.getResourceAsStream("version.properties").use {
            props.load(it)
        }
        return props.getProperty("version")
    }

    private fun getTotalDuration(): Long {
        return TestInteractionsLog.testHttpLogMessages.sumOf { it.duration() }
    }

    private fun groupScenarios(): MutableMap<String, MutableMap<String, MutableMap<Int, MutableList<ScenarioData>>>> {
        val testData: MutableMap<String, MutableMap<String, MutableMap<Int, MutableList<ScenarioData>>>> = mutableMapOf()

        for ((path, methodGroup) in groupedTestResultRecords) {
            for ((method, statusGroup) in methodGroup) {
                val methodMap = testData.getOrPut(path) { mutableMapOf() }

                for ((status, testResults) in statusGroup) {
                    val statusMap = methodMap.getOrPut(method) { mutableMapOf() }
                    val scenarioDataList = statusMap.getOrPut(status) { mutableListOf() }

                    for (test in testResults) {
                        val matchingLogMessage = groupedHttpLogMessages[method]?.firstOrNull {
                            it.scenario == test.scenarioResult?.scenario
                        }
                        val scenarioName = getTestName(test, matchingLogMessage)
                        val htmlResult = categorizeResult(test)

                        scenarioDataList.add(
                            ScenarioData(
                                name = scenarioName,
                                url = matchingLogMessage?.targetServer ?: "Unknown URL",
                                duration = matchingLogMessage?.duration() ?: 0,
                                remark = test.result.toString(),
                                valid = test.isValid,
                                wip = test.isWip,
                                request = getRequestString(matchingLogMessage),
                                requestTime = matchingLogMessage?.requestTime?.toEpochMillis() ?: 0,
                                response = getResponseString(matchingLogMessage),
                                responseTime = matchingLogMessage?.responseTime?.toEpochMillis() ?: 0,
                                specFileName = getSpecFileName(test, matchingLogMessage),
                                result = htmlResult,
                                details = getReportDetail(scenarioName, test, htmlResult)
                            )
                        )
                    }
                }
            }
        }

        return testData
    }

    private fun getTestName(testResult: TestResultRecord, httpLogMessage: HttpLogMessage?): String {
        return httpLogMessage?.displayName() ?: "Scenario: ${testResult.path} -> ${testResult.responseStatus}"
    }

    private fun getResponseString(httpLogMessage: HttpLogMessage?): String {
        return httpLogMessage?.response?.toLogString() ?: "No Response"
    }

    private fun getReportDetail(scenarioName: String, testResult: TestResultRecord, htmlResult: HtmlResult): String {
        val details = testResult.scenarioResult?.reportString()
        val scenarioDetail = "$scenarioName ${htmlResultToDetail(htmlResult)}"
        return if (details.isNullOrEmpty())
            scenarioDetail
        else
            "$scenarioDetail\n$details"
    }

    private fun htmlResultToDetail(htmlResult: HtmlResult): String {
        return when (htmlResult) {
            HtmlResult.Failed -> "has FAILED"
            HtmlResult.Error -> "has ERROR-ED"
            HtmlResult.Skipped -> "has been SKIPPED"
            else -> "has SUCCEEDED"
        }
    }

    private fun getRequestString(httpLogMessage: HttpLogMessage?): String {
        return httpLogMessage?.request?.toLogString() ?: "No Request"
    }

    private fun getSpecFileName(testResult: TestResultRecord, httpLogMessage: HttpLogMessage?): String {
        return testResult.specification ?: httpLogMessage?.scenario?.specification ?: "Unknown Spec File"
    }

    private fun getBadgeColor(coverageRow: OpenApiCoverageConsoleRow): String {
        val testScenarios = groupedScenarios[coverageRow.path]?.get(coverageRow.method)?.get(coverageRow.responseStatus.toInt()) ?: emptyList()

        testScenarios.forEach {
            when (it.result) {
                HtmlResult.Failed, HtmlResult.Error -> return if(it.wip) "yellow" else "red"
                HtmlResult.Skipped -> return "yellow"
                else -> {}
            }
        }
        return "green"
    }

    private fun dumpTestData(testData: MutableMap<String, MutableMap<String, MutableMap<Int, MutableList<ScenarioData>>>>): String {
        val mapper = ObjectMapper()
        val json = mapper.writeValueAsString(testData)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        writeToFileToAssets(outputDirectory, "test_data.json", mapper.writeValueAsString(testData))
        return json
    }

    private fun categorizeResult(testResult: TestResultRecord): HtmlResult {
        return when(testResult.result) {
            TestResult.Success -> HtmlResult.Success
            TestResult.NotCovered-> HtmlResult.Skipped
            TestResult.Error -> if(testResult.isWip) HtmlResult.Skipped else HtmlResult.Error
            else -> if(testResult.isWip) HtmlResult.Skipped else HtmlResult.Failed
        }
    }
}

data class ScenarioData(
    val name: String,
    val url: String,
    val duration: Long,
    val remark: String,
    val valid: Boolean,
    val wip: Boolean,
    val request: String,
    val requestTime: Long,
    val response: String,
    val responseTime: Long,
    val specFileName: String,
    val result: HtmlResult,
    val details: String
)

data class TableRow(
    val pathRowSpan: Int,
    val methodRowSpan: Int,
    val showPath: Boolean,
    val showMethod: Boolean,
    val coverageRow: OpenApiCoverageConsoleRow,
    val badgeColor: String
)

enum class HtmlResult {
    Success,
    Failed,
    Error,
    Skipped
}
