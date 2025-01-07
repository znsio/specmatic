package io.specmatic.core.examples.server

data class ExampleValidationResult(
    val jsonPath: String,
    val description: String,
    val severity: Severity
)
