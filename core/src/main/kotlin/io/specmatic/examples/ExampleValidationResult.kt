package io.specmatic.examples

import io.specmatic.core.Result
import io.specmatic.core.log.consoleLog
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

    fun logErrors(index: Int? = null) {
        val prefix = index?.let { "$it. " } ?: ""

        if (this.result.isSuccess()) {
            return consoleLog("$prefix${this.exampleFile?.name ?: this.exampleName} is valid")
        }

        consoleLog("\n$prefix${this.exampleFile?.name ?: this.exampleName} has the following validation error(s):")
        consoleLog(this.result.reportString())
    }
}
