package io.specmatic.test.reports.coverage.html

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.specmatic.core.ReportFormatter
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.SuccessCriteria
import io.specmatic.core.TestResult
import io.specmatic.core.log.logger
import io.specmatic.test.reports.coverage.console.Remarks
import io.specmatic.core.*
import io.specmatic.core.log.logger
import io.specmatic.test.reports.coverage.html.HtmlTemplateConfiguration.Companion.configureTemplateEngine
import org.thymeleaf.context.Context
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class HtmlReport(private val htmlReportInput: HtmlReportInput) {
    private val outputDirectory = htmlReportInput.reportFormat.outputDirectory
    private val apiSuccessCriteria = htmlReportInput.successCriteria
    private val reportFormat = htmlReportInput.reportFormat
    private val reportData = htmlReportInput.reportData
    private val specmaticConfig = htmlReportInput.specmaticConfig

    private var totalTests = 0
    private var totalErrors = 0
    private var totalFailures = 0
    private var totalSkipped = 0
    private var totalSuccess = 0
    private var totalMissing = 0

    fun generate(launchBrowser: Boolean = true): File {
        validateData()
        createAssetsDir(outputDirectory)
        updateScenarioDataAndCalculateCounts(reportData.scenarioData)

        val outFile = File(outputDirectory, "index.html")
        val htmlText = generateHtmlReportText()
        if (!outFile.parentFile.exists()) outFile.mkdirs()
        outFile.writer().use { it.write(htmlText) }
        if(launchBrowser) {
            openFileBasedOnOS(outFile.absolutePath)
        }
        return outFile
    }

    private fun generateHtmlReportText(): String {
        val testCriteria = testCriteriaPassed()
        val successCriteria = successCriteriaPassed(reportData.totalCoveragePercentage)
        val updatedTableRows = updateTableRows(reportData.tableRows)

        val templateVariables = mapOf(
            "lite" to reportFormat.lite,
            "pageTitle" to reportFormat.title,
            "reportHeading" to reportFormat.heading,
            "logo" to reportFormat.logo,
            "logoAltText" to reportFormat.logoAltText,
            "liteMode" to reportFormat.lite,
            "summaryResult" to if (testCriteria && successCriteria) "approved" else "rejected",
            "totalCoverage" to reportData.totalCoveragePercentage,
            "totalSuccess" to totalSuccess,
            "totalFailures" to totalFailures,
            "totalErrors" to totalErrors,
            "totalSkipped" to totalSkipped,
            "totalTests" to totalTests,
            "totalDuration" to formatDuration(reportData.totalTestDuration),
            "successCriteriaPassed" to successCriteria,
            "testCriteriaPassed" to testCriteria,
            "tableColumns" to htmlReportInput.tableColumns,
            "tableRows" to updatedTableRows,
            "specmaticImplementation" to htmlReportInput.specmaticImplementation,
            "specmaticVersion" to htmlReportInput.specmaticVersion,
            "generatedOn" to generatedOnTimestamp(),
            "jsonTestData" to dumpTestData(reportData.scenarioData),
            "thresholdInfo" to htmlReportInput.successCriteria,
            "excludedEndpoints" to specmaticConfig.report!!.types.apiCoverage.openAPI.excludedEndpoints,
            "testConfig" to specmaticConfig.test,
            "contractSources" to specmaticConfig.sources,
            "exampleDirs" to specmaticConfig.examples,
            "sutInfo" to htmlReportInput.sutInfo.let {
                val mainGroupName = it.mainGroupName.ifBlank { updatedTableRows.first().groups.first().columnName }
                it.copy(mainGroupName = mainGroupName)
            }
        )

        return configureTemplateEngine().process(
            "report",
            Context().apply { setVariables(templateVariables) }
        )
    }

    private fun updateTableRows(tableRows: List<TableRow>): List<TableRow> {
        tableRows.forEach {
            val (htmlResult, badgeColor) = getHtmlResultAndBadgeColor(it)
            it.htmlResult = htmlResult
            it.badgeColor = badgeColor
        }

        return tableRows
    }

    private fun updateScenarioData(scenarioData: ScenarioDataGroup): ScenarioDataGroup {
        scenarioData.subGroup.forEach { (_, group) ->
            if (group.subGroup.isNotEmpty()) {
                updateScenarioData(group)
            } else {
                group.data.forEach {
                    val htmlResult = categorizeResult(it)
                    val scenarioDetail = "${it.name} ${htmlResultToDetailPostFix(htmlResult)}"

        scenarioData.htmlResult = htmlResult
        scenarioData.details = if (scenarioData.details.isBlank()) scenarioDetail else "$scenarioDetail\n${scenarioData.details}"

        return scenarioData
    }

    private fun testCriteriaPassed(): Boolean {
        // NOTE: Ignoring Errors, they'll only contain failing WIP Tests
        return totalFailures == 0
    }

    private fun successCriteriaPassed(totalCoveragePercentage: Int): Boolean {
        return totalCoveragePercentage >= apiSuccessCriteria.minThresholdPercentage || !apiSuccessCriteria.enforce
    }

    private fun updateScenarioDataAndCalculateCounts(scenarioDataGroup: ScenarioDataGroup) {
        scenarioDataGroup.subGroup.mapValues { (_, group) ->
            if (group.subGroup.isNotEmpty()) {
                updateScenarioDataAndCalculateCounts(group)
            } else {
                group.data.map {
                    updateScenarioData(it)
                    when (it.testResult) {
                        TestResult.MissingInSpec -> totalMissing++
                        TestResult.NotCovered -> totalSkipped++
                        TestResult.Success -> totalSuccess++
                        TestResult.Error -> totalErrors++
                        else -> if (it.wip) totalErrors++ else totalFailures++
                    }
                }
            }
        }

        totalTests = when (reportFormat.lite) {
            true ->  totalSuccess + totalFailures + totalErrors
            else ->  totalSuccess + totalFailures + totalErrors + totalSkipped
        }
    }

    private fun generatedOnTimestamp(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy h:mma", Locale.ENGLISH)
        return currentDateTime.format(formatter)
    }

    private fun getHtmlResultAndBadgeColor(tableRow: TableRow): Pair<HtmlResult, String> {
        var scenarioDataGroup = reportData.scenarioData

        tableRow.groups.forEach {
            scenarioDataGroup = scenarioDataGroup.subGroup[it.value]!!
        }

        scenarioDataGroup.data.forEach { scenario ->
            if (!scenario.valid) return Pair(scenario.htmlResult!!, "red")

            when (scenario.htmlResult) {
                HtmlResult.Failed -> return Pair(HtmlResult.Failed, "red")
                HtmlResult.Error -> return Pair(HtmlResult.Error, "yellow")
                HtmlResult.Skipped -> return Pair(HtmlResult.Skipped, "yellow")
                else -> {}
            }
        }

        return Pair(HtmlResult.Success, "green")
    }

    private fun htmlResultToDetailPostFix(htmlResult: HtmlResult): String {
        return when (htmlResult) {
            HtmlResult.Skipped -> "has been SKIPPED"
            HtmlResult.Error -> "has ERROR"
            HtmlResult.Success -> "has SUCCEEDED"
            else -> "has FAILED"
        }
    }

    private fun categorizeResult(scenarioData: ScenarioData): HtmlResult {
        return when (scenarioData.testResult) {
            TestResult.Success -> HtmlResult.Success
            TestResult.NotCovered -> HtmlResult.Skipped
            TestResult.Error -> HtmlResult.Error
            else -> if (scenarioData.wip) HtmlResult.Error else HtmlResult.Failed
        }
    }

    private fun dumpTestData(testData: ScenarioDataGroup): String {
        val mapper = ObjectMapper()
        val json = mapper.writeValueAsString(testData)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        writeToFileToAssets(outputDirectory, "test_data.json", mapper.writeValueAsString(testData))
        return json
    }

    private fun openFileBasedOnOS(fileAbsPath: String) {
        val os = System.getProperty("os.name").lowercase()

        try {
            when {
                os.contains("win") -> {
                    val command = listOf("rundll32", "url.dll,FileProtocolHandler", fileAbsPath)
                    ProcessBuilder(command).start()
                }

                os.contains("mac") -> {
                    val command = listOf("open", fileAbsPath)
                    ProcessBuilder(command).start()
                }

                os.contains("nux") || os.contains("nix") -> {
                    val command = listOf("xdg-open", fileAbsPath)
                    ProcessBuilder(command).start()
                }

                else -> {
                    logger.log("Could not launch browser. Unsupported OS: $os")
                }
            }
        } catch (e: IOException) {
            logger.log("Could not launch browser. Exception: $e")
        }
    }

    private fun validateData() {
        val tableColumns = htmlReportInput.tableColumns
        val scenarioDataGroup = reportData.scenarioData

        if (tableColumns.isEmpty()) {
            throw IllegalArgumentException("Table columns cannot be empty.")
        }

        if (scenarioDataGroup.subGroup.isEmpty()) {
            throw IllegalArgumentException("Scenario data group cannot be empty.")
        }

        reportData.tableRows.forEach { rowGroup ->
            if (rowGroup.groups.size != tableColumns.size) {
                throw RowGroupSizeMismatchException("Row groups length ${rowGroup.groups.size} does not match table columns size ${tableColumns.size} in rowGroup: $rowGroup")
            }
            rowGroup.groups.mapIndexed { index, row ->
                if (row.columnName != tableColumns[index].name) {
                    throw TableOrderMismatchException("Table groups must be in order. Expected ${tableColumns[index].name} but got ${row.columnName} in row $row")
                }
            }
        }

        val maxScenarioDataDepth = findMaxDepth(scenarioDataGroup)
        if (maxScenarioDataDepth != tableColumns.size) {
            throw DepthMismatchException("Scenario data group depth $maxScenarioDataDepth does not match table columns size ${tableColumns.size}")
        }
    }

    private fun findMaxDepth(group: ScenarioDataGroup, currentDepth: Int = 0): Int {
        if (group.subGroup.isEmpty()) {
            return currentDepth
        }

        val subGroupDepths = group.subGroup.map { subGroup ->
            findMaxDepth(subGroup.value, currentDepth + 1)
        }

        return subGroupDepths.maxOrNull() ?: currentDepth
    }

    private fun formatDuration(durationInMilliseconds: Long): String {
        val minutes = (durationInMilliseconds / 60000).toInt()
        val seconds = ((durationInMilliseconds % 60000) / 1000).toInt()
        val milliseconds = (durationInMilliseconds % 1000).toInt()

        return "%02d:%02d.%03d".format(minutes, seconds, milliseconds)
    }
}
