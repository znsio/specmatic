package io.specmatic.test.reports.renderers

interface ReportRenderer<T> {
    fun render(report: T): String
}