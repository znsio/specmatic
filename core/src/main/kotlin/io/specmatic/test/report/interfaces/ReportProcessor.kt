package io.specmatic.test.report.interfaces

import io.specmatic.core.ReportConfiguration
import io.specmatic.core.SpecmaticConfig

interface ReportProcessor<T> {
    fun process(specmaticConfig: SpecmaticConfig)

    fun configureReportRenderers(reportConfiguration: ReportConfiguration): List<ReportRenderer<T>>

    fun assertSuccessCriteria(reportConfiguration: ReportConfiguration, report: T)
}
