package run.qontract.proxy

interface FileWriter {
    fun createDirectory()
    fun writeText(path: String, content: String)
}