package `in`.specmatic.test.reports

import `in`.specmatic.core.ReportConfiguration
import `in`.specmatic.core.ReportFormatterType
import `in`.specmatic.core.log.logger
import `in`.specmatic.test.reports.coverage.OpenAPICoverageReport
import `in`.specmatic.test.reports.renderers.CoverageReportTextRenderer
import `in`.specmatic.test.reports.renderers.ReportRenderer

class ReportPrinter(reportConfiguration: ReportConfiguration) {
    private var coverageReportRenderers:MutableList<ReportRenderer<OpenAPICoverageReport>> = mutableListOf()
    init {
        reportConfiguration.formatters!!.map {
            when (it.type) {
                ReportFormatterType.TEXT -> CoverageReportTextRenderer()
                else -> throw Exception("Report formatter type: ${it.type} is not supported")
            }
        }.let { coverageReportRenderers.addAll(it) }
    }

    fun printAPICoverageReport(apiCoverageReport: OpenAPICoverageReport) {
        if(apiCoverageReport.rows.isEmpty()){
            return
        }
        logger.newLine()
        coverageReportRenderers.forEach{
            logger.log(it.render(apiCoverageReport))
        }
    }
}