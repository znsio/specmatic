package application

import java.io.File

class RealFileReader: FileReader {
    override fun read(name: String): String {
        return File(name).readText()
    }
}
