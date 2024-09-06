package io.specmatic.test.report.interfaces

import io.specmatic.core.ReportConfiguration
import io.specmatic.core.SpecmaticConfig

interface ReportProcessor {
    fun process(specmaticConfig: SpecmaticConfig)

    fun configureReportRenderers(reportConfiguration: ReportConfiguration): List<ReportRenderer>

    fun assertSuccessCriteria(reportConfiguration: ReportConfiguration, report: ReportInput)
}
