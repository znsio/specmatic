package `in`.specmatic.test.reports.coverage.json

import kotlinx.serialization.Serializable

@Serializable
data class OpenApiCoverageOperation(
    val path: String,
    val method: String,
    val responseCode: Int = 0,
    val count: Int = 0,
    val coverageStatus: String
)