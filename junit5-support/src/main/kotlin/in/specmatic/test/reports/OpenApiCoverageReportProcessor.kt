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
        assertFailureCriteria(reportConfiguration,openAPICoverageReport)
    }

    private fun configureOpenApiCoverageReportRenderers(reportConfiguration: ReportConfiguration): List<ReportRenderer<OpenAPICoverageReport>> {
        return reportConfiguration.formatters!!.map {
            when (it.type) {
                ReportFormatterType.TEXT -> CoverageReportTextRenderer()
                else -> throw Exception("Report formatter type: ${it.type} is not supported")
            }
        }
    }

    private fun assertFailureCriteria(
        reportConfiguration: ReportConfiguration,
        openAPICoverageReport: OpenAPICoverageReport
    ) {
        val failureCriteria = reportConfiguration.types.apiCoverage.openAPI.failureCriteria
        if (failureCriteria.enforce) {
            val coverageThresholdNotMetMessage =
                "Total coverage: ${openAPICoverageReport.totalCoveragePercentage}% is less than the specified minimum threshold of ${failureCriteria.minThresholdPercentage}%"
            val missedEndpointsCountExceededMessage =
                "Total missed endpoints count: ${openAPICoverageReport.missedEndpointsCount} is greater than the maximum threshold of ${failureCriteria.maxMissedEndpointsInSpec}"
            val minCoverageThresholdNotMet = openAPICoverageReport.totalCoveragePercentage < failureCriteria.minThresholdPercentage
            val maxMissingEndpointsExceeded = openAPICoverageReport.missedEndpointsCount > failureCriteria.maxMissedEndpointsInSpec
            if(minCoverageThresholdNotMet || maxMissingEndpointsExceeded){
                logger.newLine()
                logger.log("The following failure criteria specified for the OpenAPI Coverage Report was not met:")
                if(minCoverageThresholdNotMet) {
                    logger.log(coverageThresholdNotMetMessage)
                }
                if(maxMissingEndpointsExceeded) {
                    logger.log(missedEndpointsCountExceededMessage)
                }
                logger.newLine()
            }
            assertThat(openAPICoverageReport.totalCoveragePercentage).withFailMessage(coverageThresholdNotMetMessage)
                .isGreaterThanOrEqualTo(failureCriteria.minThresholdPercentage)
            assertThat(openAPICoverageReport.missedEndpointsCount).withFailMessage(missedEndpointsCountExceededMessage)
                .isLessThanOrEqualTo(failureCriteria.maxMissedEndpointsInSpec)
        }
    }
}