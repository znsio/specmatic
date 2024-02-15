package `in`.specmatic.core.utilities

import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.log.logger
import java.io.File
import java.net.ServerSocket
import java.net.URL

class WebSource(override val testContracts: List<String>, override val stubContracts: List<String>) : ContractSource {
    override val type: String = "web"
    override fun pathDescriptor(path: String): String {
        return ""
    }

    override fun install(workingDirectory: File) {
    }

    override fun directoryRelativeTo(workingDirectory: File): File {
        return File(".")
    }

    override fun getLatest(sourceGit: SystemGit) {
        logger.log("No need to get latest as this source is a URL")
    }

    override fun pushUpdates(sourceGit: SystemGit) {
        logger.log("No need to push updates as this source is a URL")
    }

    fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

    override fun loadContracts(
        selector: ContractsSelectorPredicate,
        workingDirectory: String,
        configFilePath: String
    ): List<ContractPathData> {
        val resolvedPath = File(workingDirectory).resolve("web")
        return selector.select(this).map {
            val url = URL(it)
            val path = url.path.removePrefix("/")

            val contractPath = resolvedPath.resolve(path).canonicalFile
            contractPath.parentFile.mkdirs()

            download(url, contractPath)

            ContractPathData(
                resolvedPath.path,
                contractPath.path,
                provider = type,
                specificationPath = contractPath.canonicalPath
            )
        }
    }

    private fun download(url: URL, specificationFile: File): File {
        val connection = url.openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connect()

        val inputStream = connection.getInputStream()
        val outputStream = specificationFile.outputStream()

        inputStream.copyTo(outputStream)

        inputStream.close()
        outputStream.close()

        if (specificationFile.extension in CONTRACT_EXTENSIONS)
            return specificationFile

        val text = specificationFile.readText().trim()
        val extension = if (text.startsWith("{")) {
            "json"
        } else {
            "yaml"
        }

        val renamedFile = File(specificationFile.path + ".$extension")
        specificationFile.renameTo(renamedFile)

        return renamedFile
    }
}