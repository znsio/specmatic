package io.specmatic.examples

import io.specmatic.core.Result
import java.io.File

enum class ExampleType(val value: String) {
    INLINE("Inline"),
    EXTERNAL("External");

    override fun toString(): String {
        return this.value
    }
}

data class ExampleValidationResult(val exampleName: String, val result: Result, val type: ExampleType, val exampleFile: File? = null) {
    constructor(exampleFile: File, result: Result) : this(exampleFile.nameWithoutExtension, result, ExampleType.EXTERNAL, exampleFile)
}
