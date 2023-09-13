package `in`.specmatic.test.reports.coverage.json

import kotlinx.serialization.Serializable

@Serializable
data class OpenApiCoverageJsonRow(
    val type: String? = null,
    val repository: String? = null,
    val branch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null,
    val operations: List<OpenApiCoverageOperation>
)