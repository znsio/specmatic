package io.specmatic.test.reports.renderers

import io.specmatic.core.ReportConfiguration

interface ReportRenderer<T> {
    fun render(report: T, reportConfiguration: ReportConfiguration): String
}