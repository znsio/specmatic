package `in`.specmatic.test.reports.coverage.json

import kotlinx.serialization.Serializable

@Serializable
data class OpenApiCoverageJsonRow(
    val type: String?,
    val repository: String?,
    val branch: String?,
    val specification: String?,
    val serviceType: String?,
    val operations: List<OpenApiCoverageOperation>
)