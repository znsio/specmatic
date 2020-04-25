package application.versioning.commands

import java.io.File

data class ExistingFile(val filePath: String) {
    constructor(filePath: File) : this(filePath.absolutePath)

    val file = File(filePath)

    fun writeText(text: String) {
        file.writeText(text)
    }

    init {
        file.parentFile.mkdirs()
        if(!file.exists()) file.createNewFile()
    }
}
