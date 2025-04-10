package io.specmatic.core.examples.server

import java.io.File

data class FixExampleResponse(
    val exampleFile: File,
    val errorMessage: String? = null
)
