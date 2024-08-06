package io.specmatic.test.reports.coverage.html

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.specmatic.core.TestResult
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.log.logger
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_REPORT_DIRECTORY
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import io.specmatic.test.DataRecorder
import io.specmatic.test.DataRecorder.displayName
import io.specmatic.test.DataRecorder.duration
import io.specmatic.test.SpecmaticJUnitSupport
import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class HtmlReport {
    companion object {
        private val testResultRecords = SpecmaticJUnitSupport.openApiCoverageReportInput.finalizedTestResultRecords

        private val groupedHttpLogMessages = DataRecorder.testHttpLogMessages.groupBy { it.scenario?.method }

        private val apiCoverageRows = SpecmaticJUnitSupport.openApiCoverageReportInput.apiCoverageRows
            .groupBy { it.endpointPath }.mapValues { pathGroup -> pathGroup.value.groupBy { it.endpointMethod } }

        private val groupedTestResultRecords = testResultRecords.groupBy { it.path }
            .mapValues { pathGroup ->
                pathGroup.value.groupBy { it.method }
                    .mapValues { methodGroup -> methodGroup.value.groupBy { it.responseStatus } }
            }

        private val outputDirectory =(getStringValue(SPECMATIC_REPORT_DIRECTORY) ?: "reports/specmatic") + "/html"
        internal val successResultSet = setOf(TestResult.Success, TestResult.DidNotRun, TestResult.Covered)

        private val totalTests: Int = testResultRecords.size
        private var totalErrors = 0
        private var totalFailures = 0
        private var totalSkipped = 0
        private var totalSuccess = 0
    }

    fun generate() {
        logger.log("Generating HTML report...")
        val testData = groupScenarios()
        val jsonTestData = dumpTestData(testData)
        createAssetsDir(outputDirectory)
        calculateTestGroupCounts()
        val outFile = File(outputDirectory, "index.html")
        val htmlText = """
<!DOCTYPE html>
<html lang="en" class="scroll-smooth">
<head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="assets/favicon.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:ital,wght@0,100;0,300;0,400;0,500;0,700;0,900;1,100;1,300;1,400;1,500;1,700;1,900&display=swap" rel="stylesheet">
    <title>Specmatic Report</title>
    <link rel="stylesheet" href="assets/styles.css">
</head>
<body class="flex flex-col min-h-screen gap-3 p-4 font-mono">
    ${makeHeader()}
    <main class="flex items-start flex-1 overflow-hidden border-t-2 shadow-md group print:overflow-visible print:shadow-none" data-item="table">
        <table id="reports" class="font-mono overflow-hidden group-data-[item=details]:min-w-0 group-data-[item=table]:min-w-full duration-500 transition-all max-w-full">
            <thead>
                <tr class="font-bold">
                  <td>Coverage</td>
                  <td colspan="2">Path</td>
                  <td>Method</td>
                  <td>Response</td>
                  <td>Exercised</td>
                  <td>Result</td>
                </tr>
            </thead>
            <tbody>
                ${makeTableRows()}
            </tbody>
        </table>
        <div id="details" class="print:hidden group-data-[item=table]:min-w-0 group-data-[item=details]:min-w-full duration-500 transition-all p-2 flex-shrink-0 max-w-full">
            <div class="flex gap-3 top-2" id="response-details">
                <button id="go-back" class="px-6 py-2 text-white duration-500 bg-blue-500 rounded-md hover:bg-blue-700 group">
                    <span class="text-lg">&larr;</span>
                    <span>Go Back</span>
                </button>
                <ul id="response-summary" class="flex items-center justify-between flex-1 px-10 gap-10 border-2 border-red-300 rounded-md font-roboto">
                </ul>
            </div>
            <ul id="scenarios" class="flex flex-col gap-3 py-2 mt-3">
            </ul>
        </div>
    </main>
    ${makeFooter()}
    <script id="json-data" type="application/json">
        $jsonTestData
    </script>
    <script defer type="text/javascript" src="assets/index.js"></script>
</body>
</html>
        """.trim()
        if (!outFile.parentFile.exists()) {
            outFile.mkdirs()
        }
        outFile.writer().use {
            it.write(htmlText)
        }
    }

    private fun makeHeader(): String {
        val successRate =  if (totalTests > 0) (totalSuccess * 100 / totalTests) else 100
        val hasFailed = totalFailures > 0 || totalErrors > 0
        val summaryResult = if (hasFailed) "rejected" else "approved"

        return """
    <header class="border-2 shadow-md">
        <div class="flex items-center justify-between p-4" id="banner">
          <img src="assets/specmatic-logo.svg" alt="Specmatic" class="w-1/5 h-14">
          <h2 class="text-3xl font-medium font-roboto">Contract Test Results</h2>
          <button class="p-2 duration-100 border-2 active:scale-105 hover:border-blue-500" id="downloadButton">
            <img src="assets/download.svg" alt="Download Button" class="size-8">
          </button>
        </div>
        <div id="summary" class="flex justify-between gap-5 p-1 border-2 bg-$summaryResult">
          <ol id="results" class="flex flex-wrap items-center justify-between flex-1 px-2 gap-x-5">
            <li class="flex items-center gap-2" id="success">
              <img src="assets/trend-up.svg" alt="success rate" class="size-8 $summaryResult">
              <p>Success Rate: $successRate%</p>
            </li>
            <li class="flex items-center gap-2" id="success">
              <img src="assets/check-badge.svg" alt="success" class="size-8">
              <p>Success: $totalSuccess</p>
            </li>
            <li class="flex items-center gap-2" id="failed">
              <img src="assets/x-circle.svg" alt="failed" class="size-8">
              <p>Failed: $totalFailures</p>
            </li>
            <li class="flex items-center gap-2" id="errors">
              <img src="assets/exclamation-triangle.svg" alt="errors" class="size-8">
              <p>Errors: $totalErrors</p>
            </li>
            <li class="flex items-center gap-2" id="skipped">
              <img src="assets/blocked.svg" alt="skipped" class="size-8">
              <p>Skipped: $totalSkipped</p>
            </li>
            <li class="flex items-center gap-2" id="total-tests">
              <img src="assets/clipboard-document-list.svg" alt="total-tests" class="size-8">
              <p>Total Tests: $totalTests</p>
            </li>
            <li class="flex items-center gap-2" id="total-time">
              <img src="assets/clock.svg" alt="total-time" class="size-8">
              <p>Total Time: ${getTotalDuration()}ms</p>
            </li>
          </ol>
          <div id="badge" class="relative">
            <img src="assets/badge.svg" class="size-18 $summaryResult" alt="badge" data-approved="${summaryResult == "approved"}">
            <img src="assets/mark-$summaryResult.svg" alt="mark" class="absolute inset-0 z-10 mx-auto my-auto size-14">
          </div>
        </div>
    </header>
    """.trim()
    }

    private fun makeTableRows(): String {
        val builder = StringBuilder()

        apiCoverageRows.forEach { (_, methodGroup) ->
            val pathRowSpan = methodGroup.values.sumOf { it.size }

            methodGroup.entries.forEachIndexed { methodIndex, entry ->
                val coverageRowList = entry.value

                coverageRowList.forEachIndexed { testIndex, test ->
                    builder.append(
                        makeTestRow(
                            pathRowSpan,
                            coverageRowList.size,
                            methodIndex == 0 && testIndex == 0,
                            testIndex == 0,
                            test
                        )
                    ).append("\n\t\t\t\t")
                }
            }
        }

        return builder.toString()
    }

    private fun makeTestRow(
        pathRowSpan: Int,
        methodRowSpan: Int,
        showPathInfo: Boolean,
        showMethodInfo: Boolean,
        coverageRow: OpenApiCoverageConsoleRow
    ): String {
        val pathSpan = if (showPathInfo) "rowspan=\"$pathRowSpan\"" else "class=\"hidden\""
        val methodSpan = if (showMethodInfo) "rowspan=\"$methodRowSpan\"" else "class=\"hidden\""
        return """
                <tr class="capitalize">
                    <td $pathSpan>${coverageRow.endpointCoverage}%</td>
                    <td $pathSpan colspan="2">${coverageRow.endpointPath}</td>
                    <td $methodSpan>${coverageRow.endpointMethod}</td>
                    <td>${coverageRow.responseStatus}</td>
                    <td>${coverageRow.count}</td>
                    <td>
                        <span class="px-4 py-1.5 font-medium bg-${getBadgeColor(coverageRow.remarks)}-300 rounded-lg">${coverageRow.remarks}</span>
                    </td>
                </tr>
        """.trim()
    }

    private fun makeFooter(): String {
        return """
    <footer class="flex items-center justify-between p-2 print:flex-col print:gap-2 mt-auto">
        <p>Generated On: <span class="font-mono">${generatedOnTimestamp()}</span></p>
        <div class="flex items-center gap-2">
            <span class="whitespace-nowrap">Powered By</span>
            <img src="assets/specmatic-logo.svg" alt="Specmatic" class="mb-2 w-44">
            <p>[${getSpecmaticVersion()}]</p>
        </div>
        <div class="flex items-center gap-1">
            <p>Copyright</p>
            <p class="text-3xl">&copy;</p>
            <p>All Rights Reserved</p>
        </div>
    </footer>
        """.trim()
    }

    private fun calculateTestGroupCounts() {
        for (test in testResultRecords) {
            when (test.result) {
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
        }
    }

    private fun generatedOnTimestamp(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy h:mma", Locale.ENGLISH)
        return currentDateTime.format(formatter)
    }

    private fun getSpecmaticVersion(): String {
        val props = Properties()
        // TODO - Remove Hardcoded Value, Read from Actual File
        return "2.0.3"
    }

    private fun getTotalDuration(): Long {
        return DataRecorder.testHttpLogMessages.sumOf { it.duration() }
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
                        val matchingLogMessage = groupedHttpLogMessages.get(method)?.firstOrNull {
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
