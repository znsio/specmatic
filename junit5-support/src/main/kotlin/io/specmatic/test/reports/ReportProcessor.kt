package io.specmatic.test.reports

import io.specmatic.core.ReportFormatter
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.SuccessCriteria
import io.specmatic.test.reports.renderers.ReportRenderer

interface ReportProcessor<T> {
    fun process(specmaticConfig: SpecmaticConfig)

    fun configureReportRenderers(reportFormatters: List<ReportFormatter>): List<ReportRenderer<T>>

    fun assertSuccessCriteria(successCriteria: SuccessCriteria, report: T)
}
