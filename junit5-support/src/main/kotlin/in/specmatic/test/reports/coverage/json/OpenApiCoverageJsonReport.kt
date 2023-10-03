package `in`.specmatic.test.reports.coverage.json

import kotlinx.serialization.Serializable

@Serializable
data class OpenApiCoverageJsonReport(
    val specmaticConfigPath:String,
    val apiCoverage:List<OpenApiCoverageJsonRow>
)