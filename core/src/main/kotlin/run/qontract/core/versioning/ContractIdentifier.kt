package run.qontract.core.versioning

import run.qontract.core.POINTER_EXTENSION
import run.qontract.core.qontractDirPath
import java.io.File

data class ContractIdentifier(val name: String, val version: Int) {
    val displayableString = "$name: $version"

    private val nameAsRelativePath = name.split(".").joinToString(File.separator)
    private val cachePathToName = "$qontractDirPath/cache/$nameAsRelativePath"
    fun getCacheDescriptorFile() = File("$cachePathToName/$version.$POINTER_EXTENSION")

    fun incrementedVersion(): ContractIdentifier = copy(version = version + 1)
}

fun findLatestVersion(contractName: String): Int? {
    return File(contractNameToCacheDirectory(contractName)).listFiles()
            ?.filter { it.isFile }
            ?.map { it.name.removeSuffix(".$POINTER_EXTENSION").toInt() }
            ?.max()
}

fun contractNameToCacheDirectory(name: String): String = "$qontractDirPath/cache/${contractNameToRelativePath(name)}"
fun contractNameToRelativePath(name: String): String = name.split(".").joinToString(File.separator)

fun pathToFile(vararg pieces: String): File = File(pieces.joinToString(File.separator))
