package io.specmatic.core.examples.server

data class ValidateExampleResponse(
    val absPath: String,
    val errorMessage: String? = null,
    val errorList: List<Map<String, String>> = emptyList(),
    val isPartialFailure: Boolean = false
)
