package `in`.specmatic.core.utilities

import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import java.io.File
import java.net.ServerSocket
import java.net.URL

class WebSource(override val testContracts: List<String>, override val stubContracts: List<String>) : ContractSource {
    override val type: String = "web"
    override fun pathDescriptor(path: String): String {
        return ""
    }

    override fun install(workingDirectory: File) {
        logger.log("Install is not currently supported for web sources")
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
        return selector.select(this).map { url ->
            val path = toSpecificationPath(URL(url))

            val initialDownloadPath = resolvedPath.resolve(path).canonicalFile
            initialDownloadPath.parentFile.mkdirs()

            val actualDownloadPath = download(URL(url), initialDownloadPath)

            ContractPathData(
                resolvedPath.path,
                actualDownloadPath.path,
                provider = type,
                specificationPath = initialDownloadPath.canonicalPath
            )
        }
    }

    private fun toSpecificationPath(url: URL): String {
        val path = url.host + "/" + url.path.removePrefix("/")
        return path
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
        if (!specificationFile.renameTo(renamedFile))
            throw ContractException("Could not rename file $specificationFile to $renamedFile")

        return renamedFile
    }
}