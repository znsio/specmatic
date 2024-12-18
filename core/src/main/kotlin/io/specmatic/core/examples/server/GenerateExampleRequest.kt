package io.specmatic.core.examples.server

data class GenerateExampleRequest(
    val method: String,
    val path: String,
    val responseStatusCode: Int,
    val contentType: String? = null,
    val bulkMode: Boolean = false,
    val isSchemaBased: Boolean = false
)
