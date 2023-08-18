package `in`.specmatic.test.reports

import `in`.specmatic.core.ReportFormatter
import `in`.specmatic.core.ReportFormatterType
import `in`.specmatic.core.log.logger
import `in`.specmatic.test.reports.coverage.APICoverageReport
import `in`.specmatic.test.reports.formatters.CoverageReportTextRenderer
import `in`.specmatic.test.reports.formatters.ReportRenderer

class ReportPrinter(formatters: List<ReportFormatter>) {
    private var coverageReportRenderers:MutableList<ReportRenderer<APICoverageReport>> = mutableListOf()
    init {
        formatters.map {
            when (it.type) {
                ReportFormatterType.TEXT -> CoverageReportTextRenderer()
                else -> throw Exception("Report formatter type: ${it.type} is not supported")
            }
        }.let { coverageReportRenderers.addAll(it) }
    }

    fun printAPICoverageReport(apiCoverageReport: APICoverageReport) {
        if(apiCoverageReport.rows.isEmpty()){
            return
        }
        logger.newLine()
        coverageReportRenderers.forEach{
            logger.log(it.render(apiCoverageReport))
        }
    }
}