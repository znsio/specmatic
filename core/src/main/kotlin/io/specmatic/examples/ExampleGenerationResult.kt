package io.specmatic.examples

import java.io.File

enum class ExampleGenerationStatus(val value: String) {
    CREATED("Created"),
    EXISTS("Exists"),
    ERROR("Error");

    override fun toString(): String {
        return this.value
    }
}

data class ExampleGenerationResult(val exampleFile: File? = null, val status: ExampleGenerationStatus)