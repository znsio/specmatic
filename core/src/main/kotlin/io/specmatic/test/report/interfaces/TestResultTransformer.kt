package io.specmatic.test.report.interfaces

interface TestResultTransformer {
    fun toReportInput(): ReportInput
}