package io.specmatic.core.examples.server

import java.io.File

data class FixExampleRequest(
    val method: String,
    val path: String,
    val responseStatusCode: Int,
    val contentType: String? = null,
    val isSchemaBased: Boolean = false,
    val exampleFile: File
)
