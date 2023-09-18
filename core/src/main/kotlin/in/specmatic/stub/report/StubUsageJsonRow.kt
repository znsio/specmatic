package `in`.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageJsonRow(
    val type: String? = null,
    val repository: String? = null,
    val branch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null,
    val operations: List<StubUsageOperation>
)
