package io.specmatic.core.examples.server

data class ValidateExampleResponseMap(
    val absPath: String,
    val error: List<Map<String, Any?>> = emptyList(),
    val isPartialFailure: Boolean = false
)