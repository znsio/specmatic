package `in`.specmatic.test.reports

import `in`.specmatic.core.ReportConfiguration
import `in`.specmatic.core.ReportFormatterType
import `in`.specmatic.core.log.logger
import `in`.specmatic.test.reports.coverage.OpenAPICoverageReport
import `in`.specmatic.test.reports.coverage.OpenApiCoverageReportGenerator
import `in`.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import `in`.specmatic.test.reports.renderers.CoverageReportTextRenderer
import `in`.specmatic.test.reports.renderers.ReportRenderer
import org.assertj.core.api.Assertions.assertThat

class ReportProcessor(private val reportConfiguration: ReportConfiguration) {

    fun process(openApiCoverageReportInput: OpenApiCoverageReportInput) {
        val reportTypes = reportConfiguration.types
        if (reportTypes == null) {
            logger.log("No report types defined in specmatic.json")
            return
        }

        reportTypes.apiCoverage?.let {
            it.openAPI?.let { openApi ->
                openApiCoverageReportInput.addExcludedAPIs(openApi.excludedEndpoints)
                val openAPICoverageReport = OpenApiCoverageReportGenerator(openApiCoverageReportInput).generate()
                if (openAPICoverageReport.rows.isEmpty()) {
                    logger.log("The Open API coverage report generated is blank")
                    return
                }
                val renderers  = configureOpenApiCoverageReportRenderers()
                renderers.forEach { renderer ->
                    logger.log(renderer.render(openAPICoverageReport))
                }
                assertFailureCriteria(openAPICoverageReport)
            }
        }
    }

    private fun configureOpenApiCoverageReportRenderers(): List<ReportRenderer<OpenAPICoverageReport>> {
        return reportConfiguration.formatters!!.map {
            when (it.type) {
                ReportFormatterType.TEXT -> CoverageReportTextRenderer()
                else -> throw Exception("Report formatter type: ${it.type} is not supported")
            }
        }
    }

    private fun assertFailureCriteria(openAPICoverageReport: OpenAPICoverageReport) {
        val failureCriteria = reportConfiguration.types?.apiCoverage?.openAPI?.failureCriteria
        if (failureCriteria?.enforce == true) {
            assertThat(openAPICoverageReport.totalCoveragePercentage).withFailMessage("Total coverage: ${openAPICoverageReport.totalCoveragePercentage}% is less than the specified minimum threshold of ${failureCriteria.minThresholdPercentage}%")
                .isGreaterThanOrEqualTo(failureCriteria.minThresholdPercentage)
            assertThat(openAPICoverageReport.missedEndpointsCount).withFailMessage("Total missed endpoints count: ${openAPICoverageReport.missedEndpointsCount}% is greater than the maximum threshold of ${failureCriteria.maxMissedEndpointsInSpec}")
                .isLessThanOrEqualTo(failureCriteria.maxMissedEndpointsInSpec)
        }
    }
}