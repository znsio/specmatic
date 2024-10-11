package io.specmatic.examples

import java.io.File

enum class ExampleGenerationStatus(val value: String) {
    CREATED("Inline"),
    ERROR("External"),
    EXISTS("Exists");

    override fun toString(): String {
        return this.value
    }
}

data class ExampleGenerationResult(val exampleFile: File? = null, val status: ExampleGenerationStatus)