package io.specmatic.core.examples.server

data class ValidateExampleRequest(
    val method: String,
    val path: String,
    val responseStatusCode: Int,
    val contentType: String? = null,
    val isSchemaBased: Boolean = false,
    val exampleFile: String
)
