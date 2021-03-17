package `in`.specmatic.proxy

interface FileWriter {
    fun createDirectory()
    fun writeText(path: String, content: String)
}