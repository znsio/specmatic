package io.specmatic.test.reports

import io.specmatic.core.ReportConfigurationDetails
import io.specmatic.core.SpecmaticConfig
import io.specmatic.test.reports.renderers.ReportRenderer

interface ReportProcessor<T> {
    fun process(specmaticConfig: SpecmaticConfig)

    fun configureReportRenderers(reportConfiguration: ReportConfigurationDetails): List<ReportRenderer<T>>

    fun assertSuccessCriteria(reportConfiguration: ReportConfigurationDetails, report: T)
}
