package `in`.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageJsonReport (
    val specmaticConfigPath:String,
    val stubUsage:List<StubUsageJsonRow>
)