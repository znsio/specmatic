package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportOperation (
    val path: String?,
    val method: String?,
    val responseCode: Int,
    val count: Int
) {
    fun isSameAs(other: StubUsageReportOperation): Boolean {
        return this.path.equals(other.path) && this.method.equals(other.method) && this.responseCode == other.responseCode
    }
}