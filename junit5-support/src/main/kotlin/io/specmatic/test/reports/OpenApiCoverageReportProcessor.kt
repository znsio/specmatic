package io.specmatic.test.reports

import io.specmatic.core.ReportConfiguration
import io.specmatic.core.ReportFormatterType
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.log.logger
import io.specmatic.test.report.interfaces.ReportInput
import io.specmatic.test.report.interfaces.ReportProcessor
import io.specmatic.test.reports.coverage.OpenApiReportInput
import io.specmatic.test.reports.coverage.json.OpenApiCoverageJsonReport
import io.specmatic.test.reports.renderers.OpenApiHtmlRenderer
import io.specmatic.test.reports.renderers.OpenApiConsoleRenderer
import io.specmatic.test.report.interfaces.ReportRenderer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class OpenApiCoverageReportProcessor (private val coverageReportInput: OpenApiReportInput): ReportProcessor {
    companion object {
        const val JSON_REPORT_PATH = "./build/reports/specmatic"
        const val JSON_REPORT_FILE_NAME = "coverage_report.json"
    }

    override fun process(specmaticConfig: SpecmaticConfig) {
        val reportConfiguration = specmaticConfig.report!!
        if (coverageReportInput.coverageRows.isEmpty()) {
            logger.log("The Open API coverage report generated is blank.\nThis can happen if you have included all the endpoints in the 'excludedEndpoints' array in the report section in specmatic.json, or if your open api specification does not have any paths documented.")
        } else {
            val renderers = configureReportRenderers(reportConfiguration)
            renderers.forEach { renderer ->
                logger.log(renderer.render(coverageReportInput, specmaticConfig))
            }
            saveAsJson(OpenApiCoverageJsonReport(coverageReportInput.configFilePath, coverageReportInput.testResultRecords))
        }
        assertSuccessCriteria(reportConfiguration,coverageReportInput)
    }

    override fun configureReportRenderers(reportConfiguration: ReportConfiguration): List<ReportRenderer> {
        return reportConfiguration.formatters!!.map {
            when (it.type) {
                ReportFormatterType.TEXT -> OpenApiConsoleRenderer()
                ReportFormatterType.HTML -> OpenApiHtmlRenderer()
                else -> throw Exception("Report formatter type: ${it.type} is not supported")
            }
        }
    }

    private fun saveAsJson(openApiCoverageJsonReport: OpenApiCoverageJsonReport) {
        println("Saving Open API Coverage Report json to $JSON_REPORT_PATH ...")
        val json = Json {
            encodeDefaults = false
        }
        val reportJson = json.encodeToString(openApiCoverageJsonReport)
        val directory = File(JSON_REPORT_PATH)
        directory.mkdirs()
        val file = File(directory, JSON_REPORT_FILE_NAME)
        file.writeText(reportJson)
    }

    override fun assertSuccessCriteria(
        reportConfiguration: ReportConfiguration,
        report: ReportInput
    ) {
        val successCriteria = reportConfiguration.types.apiCoverage.openAPI.successCriteria
        if (successCriteria.enforce) {
            val totalCoveragePercentage = report.totalCoveragePercentage()

            val coverageThresholdNotMetMessage = "Total API coverage: ${totalCoveragePercentage}% is less than the specified minimum threshold of ${successCriteria.minThresholdPercentage}%."
            val missedEndpointsCountExceededMessage = "Total missed endpoints count: ${report.statistics.missedEndpointsCount} is greater than the maximum threshold of ${successCriteria.maxMissedEndpointsInSpec}.\n(Note: Specmatic will consider an endpoint as 'covered' only if it is documented in the open api spec with at least one example for each operation and response code.\nIf it is present in the spec, but does not have an example, Specmatic will still report the particular operation and response code as 'missing in spec'.)"

            val minCoverageThresholdCriteriaMet = totalCoveragePercentage >= successCriteria.minThresholdPercentage
            val maxMissingEndpointsExceededCriteriaMet = report.statistics.missedEndpointsCount <= successCriteria.maxMissedEndpointsInSpec
            val coverageReportSuccessCriteriaMet = minCoverageThresholdCriteriaMet && maxMissingEndpointsExceededCriteriaMet

            if(!coverageReportSuccessCriteriaMet){
                logger.newLine()
                logger.log("Failed the following API Coverage Report success criteria:")
                if(!minCoverageThresholdCriteriaMet) {
                    logger.log(coverageThresholdNotMetMessage)
                }
                if(!maxMissingEndpointsExceededCriteriaMet) {
                    logger.log(missedEndpointsCountExceededMessage)
                }
                logger.newLine()
            }
            assertThat(coverageReportSuccessCriteriaMet).withFailMessage("One or more API Coverage report's success criteria were not met.").isTrue
        }
    }
}