package io.specmatic.core.examples.module

import java.io.File

data class ExamplePathInfo(val path: String, val created: Boolean, val status: ExampleGenerationStatus) {
    constructor(path: String, created: Boolean): this(path, created, if (created) ExampleGenerationStatus.CREATED else ExampleGenerationStatus.EXISTED)
    fun relativeTo(file: File): String {
        return File(path).canonicalFile.relativeTo(file.canonicalFile.parentFile).path
    }
}