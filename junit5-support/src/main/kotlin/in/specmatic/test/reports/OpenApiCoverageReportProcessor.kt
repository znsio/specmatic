package `in`.specmatic.test.reports

import `in`.specmatic.core.ReportConfiguration
import `in`.specmatic.core.ReportFormatterType
import `in`.specmatic.core.log.logger
import `in`.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import `in`.specmatic.test.reports.coverage.json.OpenApiCoverageJsonReport
import `in`.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import `in`.specmatic.test.reports.renderers.CoverageReportTextRenderer
import `in`.specmatic.test.reports.renderers.ReportRenderer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class OpenApiCoverageReportProcessor(private val openApiCoverageReportInput: OpenApiCoverageReportInput ): ReportProcessor {
    companion object {
        const val JSON_REPORT_PATH = "./build/reports/specmatic"
        const val JSON_REPORT_FILE_NAME = "coverage_report.json"
    }
    override fun process(reportConfiguration: ReportConfiguration) {
        openApiCoverageReportInput.addExcludedAPIs(reportConfiguration.types.apiCoverage.openAPI.excludedEndpoints)
        val openAPICoverageReport = openApiCoverageReportInput.generate()
        if (openAPICoverageReport.rows.isEmpty()) {
            logger.log("The Open API coverage report generated is blank.\nThis can happen if you have included all the endpoints in the 'excludedEndpoints' array in the report section in specmatic.json, or if your open api specification does not have any paths documented.")
        } else {
            val renderers = configureOpenApiCoverageReportRenderers(reportConfiguration)
            renderers.forEach { renderer ->
                logger.log(renderer.render(openAPICoverageReport))
            }
            saveAsJson(openApiCoverageReportInput.generateJsonReport())
        }
        assertSuccessCriteria(reportConfiguration,openAPICoverageReport)
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

    private fun configureOpenApiCoverageReportRenderers(reportConfiguration: ReportConfiguration): List<ReportRenderer<OpenAPICoverageConsoleReport>> {
        return reportConfiguration.formatters!!.map {
            when (it.type) {
                ReportFormatterType.TEXT -> CoverageReportTextRenderer()
                else -> throw Exception("Report formatter type: ${it.type} is not supported")
            }
        }
    }

    private fun assertSuccessCriteria(
        reportConfiguration: ReportConfiguration,
        openAPICoverageReport: OpenAPICoverageConsoleReport
    ) {
        val successCriteria = reportConfiguration.types.apiCoverage.openAPI.successCriteria
        if (successCriteria.enforce) {
            val coverageThresholdNotMetMessage =
                "Total API coverage: ${openAPICoverageReport.totalCoveragePercentage}% is less than the specified minimum threshold of ${successCriteria.minThresholdPercentage}%."
            val missedEndpointsCountExceededMessage =
                "Total missed endpoints count: ${openAPICoverageReport.missedEndpointsCount} is greater than the maximum threshold of ${successCriteria.maxMissedEndpointsInSpec}.\n(Note: Specmatic will consider an endpoint as 'covered' only if it is documented in the open api spec with atleast one example for each operation and response code.\nIf it is present in the spec, but does not have an example, Specmatic will still report the particular operation and response code as 'missing in spec'.)"

            val minCoverageThresholdCriteriaMet = openAPICoverageReport.totalCoveragePercentage >= successCriteria.minThresholdPercentage
            val maxMissingEndpointsExceededCriteriaMet = openAPICoverageReport.missedEndpointsCount <= successCriteria.maxMissedEndpointsInSpec
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