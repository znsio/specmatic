package `in`.specmatic.test.reports

import `in`.specmatic.core.ReportConfiguration

interface ReportProcessor {
    fun process(reportConfiguration: ReportConfiguration)
}
