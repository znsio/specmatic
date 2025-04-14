package application

import org.springframework.stereotype.Component
import java.io.File

@Component
class FileOperations {
    fun isFile(fileName: String): Boolean = File(fileName).isFile

    fun extensionIsNot(fileName: String, extensions: List<String>): Boolean = File(fileName).extension !in extensions
}
