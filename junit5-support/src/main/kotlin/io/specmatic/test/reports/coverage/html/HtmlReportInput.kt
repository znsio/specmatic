package io.specmatic.test.reports.coverage.html

import io.specmatic.core.ReportFormatter
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.SuccessCriteria
import io.specmatic.core.TestResult
import io.specmatic.test.report.Remarks

data class HtmlReportInput (
    val reportFormat: ReportFormatter,
    val specmaticConfig: SpecmaticConfig,
    val successCriteria: SuccessCriteria,
    val specmaticImplementation: String,
    val specmaticVersion: String,
    val tableColumns: List<TableColumn>,
    val reportData: HtmlReportData,
    val sutInfo: SutInfo
)

data class HtmlReportData(
    val totalCoveragePercentage: Int,
    val totalTestDuration: Long,
    val tableRows: List<TableRow>,
    val scenarioData: ScenarioDataGroup
)

data class ScenarioDataGroup(
    var data: List<ScenarioData> = emptyList(),
    var subGroup: MutableMap<String, ScenarioDataGroup> = mutableMapOf(),
)

data class SutInfo(
    val host: String,
    val port: String,
    val actuatorEnabled: Boolean,
    val mainGroupCount: Int,
    val mainGroupName: String = ""
)

data class ScenarioData(
    val name: String,
    val baseUrl: String,
    val duration: Long,
    val testResult: TestResult,
    val valid: Boolean,
    val wip: Boolean,
    val request: String,
    val requestTime: Long,
    val response: String,
    val responseTime: Long,
    val specFileName: String,
    var details: String,
    var htmlResult: HtmlResult? = null
)

data class TableColumn(
    val name: String,
    val colSpan: Int
)

data class TableRowGroup(
    val columnName: String,
    val value: String,
    val rowSpan: Int,
    val showRow: Boolean
)

data class TableRow(
    val coveragePercentage: Int,
    val groups: List<TableRowGroup>,
    val exercised: Int,
    val result: Remarks,
    var htmlResult: HtmlResult? = null,
    var badgeColor: String? = null
)

enum class HtmlResult {
    Success,
    Failed,
    Error,
    Skipped
}

class DepthMismatchException(message: String) : Exception(message)
class RowGroupSizeMismatchException(message: String) : Exception(message)
class TableOrderMismatchException(message: String) : Exception(message)
