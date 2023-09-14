package `in`.specmatic.test.reports.coverage.json

import kotlinx.serialization.Serializable

@Serializable
data class OpenApiCoverageJsonReport(
    val specmaticConfigPath:String? = null,
    val apiCoverage:List<OpenApiCoverageJsonRow>
)