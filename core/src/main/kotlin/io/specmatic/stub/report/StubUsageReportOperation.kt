package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportOperation(
    val path: String?,
    val method: String?,
    val responseCode: Int,
    val count: Int
) {
    fun hasSameOperationIdentifiers(other: StubUsageReportOperation): Boolean {
        return this.path.equals(other.path) && this.method.equals(other.method) && this.responseCode == other.responseCode
    }

    fun merge(other: StubUsageReportOperation): StubUsageReportOperation {
        return this.copy(count = this.count + other.count)
    }
}