package `in`.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageOperation (
    val path: String?,
    val method: String?,
    val responseCode: Int = 0,
    val count: Int = 0
)