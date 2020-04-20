package application

import java.io.File

data class FileExists(val filePath: String) {
    private val file: File = File(filePath)

    fun ifExists(fn: (File) -> Unit) {
        if(file.exists()) fn(file)
    }

    fun ifDoesNotExist(fn: (File) -> Unit) {
        if(!file.exists()) fn(file)
    }
}