package `in`.specmatic.core.log

import java.io.File
import java.util.*

class LogDirectory(directory: File, prefix: String, tag: String, extension: String): LogFile {
    constructor(directory: String, prefix: String, tag: String, extension: String): this(File(directory), prefix, tag, extension)

    val file: File

    init {
        if(!directory.exists())
            directory.mkdirs()

        val currentDate = CurrentDate()

        val name = "$prefix-${currentDate.toFileNameString()}${logFileNameSuffix(tag, extension)}"

        file = directory.resolve(name)
        if(!file.exists())
            file.createNewFile()
    }

    override fun appendText(text: String) {
        file.appendText(text)
    }
}

fun logFileNameSuffix(tag: String, extension: String): String {
    return tag.let {
        if(it.isNotBlank()) "-$it" else ""
    } + extension.let {
        if(it.isNotBlank()) ".$it" else ""
    }
}

