package application

import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.jsonStringToValueMap
import java.io.File

fun findLatestVersion(contractName: String): Int? {
    return File(cachePathToName(contractName)).listFiles()
            ?.filter { it.isFile }
            ?.map { it.name.removeSuffix(".$POINTER_EXTENSION").toInt() }
            ?.max()
}

fun cachePathToName(name: String): String = "$qontractDirPath/cache/${nameAsRelativePath(name)}"
fun nameAsRelativePath(name: String): String = name.split(".").joinToString(File.separator)

data class ContractIdentifier(val contractName: String, val version: Int) {
    val displayableString = "$contractName: $version"

    private val nameAsRelativePath = contractName.split(".").joinToString(File.separator)
    private val cachePathToName = "$qontractDirPath/cache/$nameAsRelativePath"
    val cacheDescriptorFile = File("$cachePathToName/$version.$POINTER_EXTENSION")

    fun isNameFound(): Boolean = File(cachePathToName).exists()
    fun isVersionFound(): Boolean = cacheDescriptorFile.exists()
    fun exists(): Boolean = isNameFound() && isVersionFound()

    fun findPrevious(found: (ContractIdentifier) -> Unit): Boolean {
        val result = (1..version.dec()).reversed().asSequence().map { previousVersion ->
            ContractIdentifier(contractName, previousVersion)
        }.find { it.exists() }

        if(result != null) {
            found(result)
        }

        return result != null
    }

    fun incrementedVersion(): ContractIdentifier = copy(version = version + 1)
}

fun getRepoProvider(identifier: ContractIdentifier): RepoProvider {
    val pointerInfo = jsonStringToValueMap(identifier.cacheDescriptorFile.readText())
    val repoName = pointerInfo.getValue("repoName").toStringValue()

    return when(getRepoType(repoName)) {
        "git" -> GitRepoProvider(repoName)
        else -> throw ContractException("Unidentified repo type")
    }
}

fun getRepoType(repoName: String): String {
    val conf = jsonStringToValueMap(pathToFile(qontractRepoDirPath, repoName, "conf.json").readText())
    return conf.getValue("type").toStringValue()
}

fun pathToFile(vararg pieces: String): File = File(pieces.joinToString(File.separator))