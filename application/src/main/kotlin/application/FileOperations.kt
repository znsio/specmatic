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

    fun extensionIsNot(fileName: String, extension: String): Boolean = File(fileName).extension != extension
}
