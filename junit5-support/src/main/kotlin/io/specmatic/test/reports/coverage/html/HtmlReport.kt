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

class HtmlReport(report: ReportConfiguration?) {

    private val groupedTestResultRecords = SpecmaticJUnitSupport.openApiCoverageReportInput.groupedTestResultRecords
    private val groupedHttpLogMessages = TestInteractionsLog.testHttpLogMessages.groupBy { it.scenario?.method }
    private val groupedApiCoverageRows = SpecmaticJUnitSupport.openApiCoverageReportInput.apiCoverageRows
        .groupBy { it.path }.mapValues { pathGroup -> pathGroup.value.groupBy { it.method } }


    private val htmlConfig = report?.formatters?.firstOrNull { it.type == ReportFormatterType.HTML }
    private val outputDirectory = htmlConfig?.outputDirectory ?: "build/reports/specmatic/html"
    private val pageTitle = htmlConfig?.title ?: "Specmatic Report"
    private val reportHeading = htmlConfig?.heading ?: "Contract Test Results"

    private val successResultSet = setOf(TestResult.Success, TestResult.DidNotRun, TestResult.Covered)
    private var totalTests: Int = 0
    private var totalErrors = 0
    private var totalFailures = 0
    private var totalSkipped = 0
    private var totalSuccess = 0

    fun generate() {
        logger.log("Generating HTML report in $outputDirectory...")
        createAssetsDir(outputDirectory)
        calculateTestGroupCounts()

        val outFile = File(outputDirectory, "index.html")
        val htmlText = generateHtmlReportText()
        if (!outFile.parentFile.exists()) outFile.mkdirs()
        outFile.writer().use { it.write(htmlText) }
    }

    private fun generateHtmlReportText(): String {
        val jsonTestDataScript = """
            <script id="json-data" type="application/json">
                ${dumpTestData(groupScenarios())}
            </script>
        """.trimIndent()

        val templateVariables = mapOf(
            "pageTitle" to pageTitle,
            "reportHeading" to reportHeading,
            "successRate" to successRate(),
            "totalSuccess" to totalSuccess,
            "totalFailures" to totalFailures,
            "totalErrors" to totalErrors,
            "totalSkipped" to totalSkipped,
            "totalTests" to totalTests,
            "totalDuration" to getTotalDuration(),
            "generatedOn" to generatedOnTimestamp(),
            "tableRows" to tableRows(),
            "specmaticVersion" to "[${getSpecmaticVersion()}]",
            "summaryResult" to summaryResult(),
            "jsonTestDataScript" to jsonTestDataScript
        )

        return configureTemplateEngine().process(
            "report",
            Context().apply { setVariables(templateVariables) }
        )
    }

    private fun successRate() = if (totalTests > 0) (totalSuccess * 100 / totalTests) else 100

    private fun summaryResult(): String {
        return if (totalFailures > 0 || totalErrors > 0) "rejected" else "approved"
    }

    private fun tableRows(): List<TableRow> {
        return groupedApiCoverageRows.flatMap { (_, methodGroup) ->
            val pathRowSpan = methodGroup.values.sumOf { it.size }

            methodGroup.entries.flatMapIndexed { methodIndex, entry ->
                val coverageRowList = entry.value

                coverageRowList.mapIndexed { testIndex, coverageRow ->
                    TableRow(
                        pathRowSpan,
                        coverageRowList.size,
                        methodIndex == 0 && testIndex == 0,
                        testIndex == 0,
                        coverageRow,
                        getBadgeColor(coverageRow.remarks)
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
                            TestResult.Error -> totalErrors++
                            TestResult.Failed -> totalFailures++
                            TestResult.Skipped -> totalSkipped++
                            TestResult.Success -> totalSuccess++
                            TestResult.NotImplemented -> totalFailures++
                            TestResult.DidNotRun -> totalSkipped++
                            TestResult.MissingInSpec -> totalFailures++
                            TestResult.NotCovered -> totalFailures++
                            TestResult.Covered -> totalSuccess++
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
                            it.scenario == test.scenario
                        }

                        scenarioDataList.add(
                            ScenarioData(
                                name = getTestName(test, matchingLogMessage),
                                url = matchingLogMessage?.targetServer ?: "Unknown URL",
                                duration = matchingLogMessage?.duration() ?: 0,
                                result = test.result.toString(),
                                valid = test.isValid,
                                request = matchingLogMessage?.request?.toLogString() ?: "No Request",
                                requestTime = matchingLogMessage?.requestTime?.toEpochMillis() ?: 0,
                                response = getResponseString(matchingLogMessage, test.result),
                                responseTime = matchingLogMessage?.responseTime?.toEpochMillis() ?: 0,
                                specFileName = test.specification ?: matchingLogMessage?.scenario?.specification ?: "Unknown Spec File",
                                passed = test.result in successResultSet,
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

    private fun getResponseString(httpLogMessage: HttpLogMessage?, result: TestResult): String {
        if(httpLogMessage == null) {
            return "No Response"
        }

        if(httpLogMessage.response == null) {
            return httpLogMessage.exception?.message ?: "No Response"
        }

        return httpLogMessage.response?.toLogString() ?: "No Response"
    }

    private fun getBadgeColor(remark: Remarks): String {
        return when(remark) {
            Remarks.Covered -> "green"
            Remarks.DidNotRun -> "yellow"
            else -> "red"
        }
    }

    private fun dumpTestData(testData: MutableMap<String, MutableMap<String, MutableMap<Int, MutableList<ScenarioData>>>>): String {
        val mapper = ObjectMapper()
        val json = mapper.writeValueAsString(testData)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        writeToFileToAssets(outputDirectory, "test_data.json", mapper.writeValueAsString(testData))
        return json
    }
}

data class ScenarioData(
    val name: String,
    val url: String,
    val duration: Long,
    val result: String,
    val valid: Boolean,
    val request: String,
    val requestTime: Long,
    val response: String,
    val responseTime: Long,
    val specFileName: String,
    val passed: Boolean
)

data class TableRow(
    val pathRowSpan: Int,
    val methodRowSpan: Int,
    val showPathInfo: Boolean,
    val showMethodInfo: Boolean,
    val coverageRow: OpenApiCoverageConsoleRow,
    val badgeColor: String
)

