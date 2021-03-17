package application

import org.springframework.stereotype.Component
import java.io.File

@Component
class FileOperations {
    fun read(path: String): String {
        return File(path).readText()
    }

    fun readBytes(path: String): ByteArray {
        return File(path).readBytes()
    }

    fun files(stubDataDir: String): List<File> {
        return File(stubDataDir).listFiles()?.toList() ?: emptyList()
    }

    fun isFile(fileName: String): Boolean = File(fileName).isFile

    fun isJSONFile(file: File): Boolean =
        file.isFile && file.extension.equals("json", ignoreCase = true)

    fun extensionIsNot(fileName: String, extensions: List<String>): Boolean = File(fileName).extension !in extensions
}
