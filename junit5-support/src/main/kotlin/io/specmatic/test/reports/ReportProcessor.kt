package io.specmatic.test.reports

import io.specmatic.core.ReportConfiguration

interface ReportProcessor {
    fun process(reportConfiguration: ReportConfiguration)
}
