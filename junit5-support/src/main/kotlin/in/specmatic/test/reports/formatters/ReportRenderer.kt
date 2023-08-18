package `in`.specmatic.test.reports.formatters

interface ReportRenderer<T> {
    fun render(report: T): String
}