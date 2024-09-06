package io.specmatic.test.report

import io.specmatic.core.*
import io.specmatic.core.log.logger
import io.specmatic.test.report.interfaces.ReportInput
import io.specmatic.test.report.interfaces.ReportProcessor
import io.specmatic.test.report.interfaces.TestResultOutput
import java.io.File

abstract class ReportEngine(val specmaticConfig: SpecmaticConfig) {
    fun report() {
        val specmaticConfig = specmaticConfig.copy(
            report = getReportConfiguration(specmaticConfig = specmaticConfig)
        )

        val testResultOutput = getTestResultOutput(specmaticConfig.report!!)
        val reportInput = getReportInput(testResultOutput)

        val reportProcessors = getReportProcessor(reportInput)
        reportProcessors.forEach { it.process(specmaticConfig) }
    }

    abstract fun getTestResultOutput(reportConfiguration: ReportConfiguration): TestResultOutput

    abstract fun getReportInput(testResultOutput: TestResultOutput): ReportInput

    abstract fun getReportProcessor(reportInput: ReportInput): List<ReportProcessor>

    private fun getReportConfiguration(specmaticConfig: SpecmaticConfig): ReportConfiguration {
        return when (val reportConfiguration = specmaticConfig.report) {
            null -> {
                logger.log("Could not load report configuration, coverage will be calculated but no coverage threshold will be enforced")
                ReportConfiguration(
                    formatters = listOf(
                        ReportFormatter(ReportFormatterType.TEXT, ReportFormatterLayout.TABLE),
                        ReportFormatter(ReportFormatterType.HTML)
                    ), types = ReportTypes()
                )
            }

            else -> {
                val htmlReportFormatter = reportConfiguration.formatters?.firstOrNull {
                    it.type == ReportFormatterType.HTML
                } ?: ReportFormatter(ReportFormatterType.HTML)

                val textReportFormatter = reportConfiguration.formatters?.firstOrNull {
                    it.type == ReportFormatterType.TEXT
                } ?: ReportFormatter(ReportFormatterType.TEXT)

                ReportConfiguration(
                    formatters = listOf(htmlReportFormatter, textReportFormatter),
                    types = reportConfiguration.types
                )
            }
        }
    }

    fun getConfigFileWithAbsolutePath(): String = File(getConfigFileName()).canonicalPath
}