package `in`.specmatic.test.reports

import `in`.specmatic.core.ReportConfiguration
import `in`.specmatic.core.ReportFormatterType
import `in`.specmatic.core.log.logger
import `in`.specmatic.test.reports.coverage.OpenAPICoverageReport
import `in`.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import `in`.specmatic.test.reports.renderers.CoverageReportTextRenderer
import `in`.specmatic.test.reports.renderers.ReportRenderer
import org.assertj.core.api.Assertions.assertThat

class OpenApiCoverageReportProcessor(private val openApiCoverageReportInput: OpenApiCoverageReportInput ): ReportProcessor {

    override fun process(reportConfiguration: ReportConfiguration) {
        openApiCoverageReportInput.addExcludedAPIs(reportConfiguration.types.apiCoverage.openAPI.excludedEndpoints)
        val openAPICoverageReport = openApiCoverageReportInput.generate()
        if (openAPICoverageReport.rows.isEmpty()) {
            logger.log("The Open API coverage report generated is blank")
        } else {
            val renderers = configureOpenApiCoverageReportRenderers(reportConfiguration)
            renderers.forEach { renderer ->
                logger.log(renderer.render(openAPICoverageReport))
            }
        }
        assertSuccessCriteria(reportConfiguration,openAPICoverageReport)
    }

    private fun configureOpenApiCoverageReportRenderers(reportConfiguration: ReportConfiguration): List<ReportRenderer<OpenAPICoverageReport>> {
        return reportConfiguration.formatters!!.map {
            when (it.type) {
                ReportFormatterType.TEXT -> CoverageReportTextRenderer()
                else -> throw Exception("Report formatter type: ${it.type} is not supported")
            }
        }
    }

    private fun assertSuccessCriteria(
        reportConfiguration: ReportConfiguration,
        openAPICoverageReport: OpenAPICoverageReport
    ) {
        val successCriteria = reportConfiguration.types.apiCoverage.openAPI.successCriteria
        if (successCriteria.enforce) {
            val coverageThresholdNotMetMessage =
                "Total API coverage: ${openAPICoverageReport.totalCoveragePercentage}% is less than the specified minimum threshold of ${successCriteria.minThresholdPercentage}%."
            val missedEndpointsCountExceededMessage =
                "Total missed endpoints count: ${openAPICoverageReport.missedEndpointsCount} is greater than the maximum threshold of ${successCriteria.maxMissedEndpointsInSpec}."
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