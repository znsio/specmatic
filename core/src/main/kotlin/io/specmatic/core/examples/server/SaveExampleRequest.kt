package io.specmatic.core.examples.server

data class SaveExampleRequest(
    val exampleFile: String,
    val exampleContent: String
)
