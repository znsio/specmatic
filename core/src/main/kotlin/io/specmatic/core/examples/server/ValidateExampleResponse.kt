package io.specmatic.core.examples.server

data class ValidateExampleResponse(
    val absPath: String,
    val errorMessage: String? = null,
    val errorList: List<ExampleValidationResult> = emptyList(),
    val isPartialFailure: Boolean = false
)
