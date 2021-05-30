package `in`.specmatic.core

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.URISyntaxException

object ContractFile {
    private var downloadDirectoryName = "__contract_tests"
    var classPath = "classpath:$downloadDirectoryName"
    @Throws(URISyntaxException::class, IOException::class)
    fun writeContractTestFile(classLoader: ClassLoader, downloadPath: String, consumer: String, contractTest: String?) {
        ensureDirectoryExists(classLoader, downloadPath)
        val filePath = classLoader.getResource(".")
        val file = File(filePath.toURI().path + downloadPath + "/" + consumer + ".feature")
        file.createNewFile()
        val writer = FileWriter(file)
        writer.write(contractTest)
        writer.close()
    }

    @Throws(URISyntaxException::class)
    fun ensureDirectoryExists(classLoader: ClassLoader, directoryPath: String) {
        val filePath = classLoader.getResource(".")
        val file = File(filePath.toURI().path + directoryPath)
        if (!file.isDirectory) {
            file.mkdirs()
        }
    }
}