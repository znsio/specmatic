package io.specmatic.test.reports.coverage.html

import io.specmatic.core.ReportConfiguration
import io.specmatic.core.ReportFormatterType
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.renderers.ReportRenderer

class CoverageReportHtmlRenderer: ReportRenderer<OpenAPICoverageConsoleReport> {
    override fun render(report: OpenAPICoverageConsoleReport, reportConfiguration: ReportConfiguration): String {
        val htmlReportConfiguration = reportConfiguration.formatters!!.first { it.type == ReportFormatterType.HTML }
        val openApiSuccessCriteria = reportConfiguration.types.apiCoverage.openAPI.successCriteria;
        HtmlReport(htmlReportConfiguration, openApiSuccessCriteria).generate()
        return "Successfully generated HTML report in ${htmlReportConfiguration.outputDirectory}"
    }
}