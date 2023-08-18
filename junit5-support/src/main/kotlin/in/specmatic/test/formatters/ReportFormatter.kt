package `in`.specmatic.test.formatters

interface ReportFormatter<T> {
    fun format(report: T): String
}