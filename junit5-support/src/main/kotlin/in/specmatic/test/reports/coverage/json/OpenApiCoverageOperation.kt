package `in`.specmatic.test.reports.coverage.json

import `in`.specmatic.test.reports.coverage.console.Remarks
import kotlinx.serialization.Serializable

@Serializable
data class OpenApiCoverageOperation(
    val path: String,
    val method: String,
    val responseCode: Int,
    val count: Int,
    val coverageStatus: Remarks
)