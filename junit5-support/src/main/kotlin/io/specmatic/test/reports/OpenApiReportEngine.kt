package io.specmatic.test.reports

import io.specmatic.core.ReportConfiguration
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.log.CurrentDate
import io.specmatic.test.API
import io.specmatic.test.OpenApiTestResultRecord
import io.specmatic.test.report.ReportEngine
import io.specmatic.test.report.interfaces.ReportInput
import io.specmatic.test.report.interfaces.ReportProcessor
import io.specmatic.test.report.interfaces.TestResultOutput
import io.specmatic.test.reports.coverage.Endpoint
import io.specmatic.test.reports.coverage.OpenApiReportInput
import io.specmatic.test.reports.coverage.OpenApiTestResultOutput
import io.specmatic.test.reports.coverage.OpenApiTestResultTransformer

class OpenApiReportEngine(
    specmaticConfig: SpecmaticConfig,
    private val testResultRecords: List<OpenApiTestResultRecord>,
    private val endpoints: List<Endpoint>,
    private val applicationAPIs: List<API>,
    private val testStartTime: CurrentDate,
    private val excludedApis: List<String>,
    private val actuatorEnabled: Boolean
) : ReportEngine(specmaticConfig) {

    override fun getTestResultOutput(reportConfiguration: ReportConfiguration): TestResultOutput {
        return OpenApiTestResultOutput(
            configFilePath = getConfigFileWithAbsolutePath(),
            testResultRecords = testResultRecords, applicationAPIs = applicationAPIs,
            excludedAPIs = reportConfiguration.types.apiCoverage.openAPI.excludedEndpoints.plus(excludedApis),
            allEndpoints = endpoints, endpointsAPISet = actuatorEnabled,
            testStartTime = testStartTime, testEndTime = CurrentDate()
        )
    }

    override fun getReportInput(testResultOutput: TestResultOutput): ReportInput {
        return OpenApiTestResultTransformer(testResultOutput as OpenApiTestResultOutput).toReportInput()
    }

    override fun getReportProcessor(reportInput: ReportInput): List<ReportProcessor> {
        return listOf(OpenApiCoverageReportProcessor(reportInput as OpenApiReportInput))
    }
}