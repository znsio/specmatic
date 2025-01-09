package io.specmatic.core.config.v1

import io.specmatic.core.config.DEFAULT_WORKING_DIRECTORY
import java.io.File

class WorkingDirectory(private val filePath: File) {
    constructor(path: String = DEFAULT_WORKING_DIRECTORY): this(File(path))

    val path: String
        get() {
            return filePath.path
        }
}