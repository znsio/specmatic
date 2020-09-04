package application

interface FileReader {
    fun read(path: String): String
}