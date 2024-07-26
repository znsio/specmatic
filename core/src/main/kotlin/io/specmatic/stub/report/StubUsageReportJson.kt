package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportJson (
    val specmaticConfigPath:String,
    val stubUsage:List<StubUsageReportRow>
)