package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportRow(
    val type: String? = null,
    val repository: String? = null,
    val branch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null,
    val operations: List<StubUsageReportOperation>
) {
    fun isSameAs(other: StubUsageReportRow): Boolean {
        return type.equals(other.type) && repository.equals(other.repository)
                && branch.equals(other.branch) && specification.equals(other.specification)
                && serviceType.equals(other.serviceType)
    }
}
