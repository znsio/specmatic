package application

import org.springframework.stereotype.Component
import java.io.File

@Component
class RealFileReader: FileReader {
    override fun read(path: String): String {
        return File(path).readText()
    }

    fun readBytes(path: String): ByteArray {
        return File(path).readBytes()
    }

    fun files(stubDataDir: String): List<File> {
        return File(stubDataDir).listFiles()?.toList() ?: emptyList()
    }
}
