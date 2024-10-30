package io.specmatic.core.filters

data class ScenarioMetadata(
    val method: String,
    val path: String,
    val statusCode: Int,
    val header: Set<String>,
    val query: Set<String>,
    val exampleName: String
)