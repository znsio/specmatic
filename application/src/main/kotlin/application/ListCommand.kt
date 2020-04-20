package application

import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "list", mixinStandardHelpOptions = true)
class ListCommand: Callable<Unit> {
    override fun call() {
        listOfFiles(File(qontractCacheDirPath), "desc")
            .map { toRelativePath(it.absolutePath, qontractCacheDirPath) }
            .map { toContractIdentifier(it, "pointer") }
            .sortedWith(compareBy<ContractIdentifier> { it.contractName }.thenBy { it.version })
            .forEach {
                println(it.displayableString)
            }
    }
}

fun toRelativePath(filePath: String, qontractCacheDirPath: String): String =
        filePath.removePrefix(qontractCacheDirPath).removePrefix(File.separator)

fun toContractIdentifier(relativePath: String, extension: String = ""): ContractIdentifier =
        relativePath.removePrefix(File.separator).split(File.separator)
                .let { pieces ->
                    val namespace = pieces.dropLast(1).joinToString(".")
                    val versionToken = pieces.last()
                    val version = when {
                        extension.isNotBlank() -> versionToken.removeSuffix(".$extension")
                        else -> when {
                            versionToken.contains(".") -> versionToken.split(".").first()
                            else -> versionToken
                        }
                    }.toInt()

                    Pair(namespace, version)
                }.let { (name, version) ->
                    ContractIdentifier(name, version)
                }
