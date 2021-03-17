package `in`.specmatic.proxy

import java.io.File

class RealFileWriter(private val dataDir: File): FileWriter {
    constructor(dataDir: String): this(File(dataDir))

    override fun createDirectory() {
        if (!dataDir.exists()) dataDir.mkdirs()
    }

    override fun writeText(path: String, content: String) {
        dataDir.resolve(path).writeText(content)
    }

}