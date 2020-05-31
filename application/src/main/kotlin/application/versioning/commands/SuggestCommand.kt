package application.versioning.commands

import picocli.CommandLine
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import java.io.File
import java.lang.NumberFormatException
import java.util.concurrent.Callable

@CommandLine.Command(name = "suggest", mixinStandardHelpOptions = true, description = ["Suggest the version of a contract"])
class SuggestCommand : Callable<Unit> {
    @CommandLine.Parameters(index = "0", description = ["Path of the contract file"])
    var contractFilePath: String = ""

    @CommandLine.Parameters(index = "1", description = ["Directory containing versioned contracts"])
    var contractDirPath: String = ""

    override fun call() {
        val newer = Feature(File(contractFilePath).readText())

        val contractDir = File(contractDirPath)
        val majorVersionNumbers = contractDir.listFiles()?.map {
            try {
                it.name.split(".").first().toInt()
            } catch (e: NumberFormatException) {
                throw Exception("The first part of the file name ${it.name} should be the major version number of the contract.")
            }
        }?.distinct()?.sorted() ?: emptyList()

        if(majorVersionNumbers.isEmpty()) {
            println("No contracts were found in $contractDirPath.")
            return
        }

        val possibleVersions = majorVersionNumbers.mapNotNull { majorVersionNumber ->
            val olderFilePath: String? = when (val compatibilityResult = try {
                testBackwardCompatibilityInDirectory(contractDir, majorVersionNumber, null)
            } catch (e: ContractException) {
                null
            }) {
                is TestResults -> when {
                    compatibilityResult.list.all { it.results.success() } -> compatibilityResult.list.last().newer
                    else -> null
                }
                is JustOne -> compatibilityResult.filePath
                NoContractsFound -> throw Exception("There should have been a contract. Something is wrong.")
                else -> null
            }

            when (olderFilePath) {
                null -> null
                else -> {
                    val older = Feature(File(olderFilePath).readText())
                    try {
                        val results = testBackwardCompatibility2(older, newer)

                        when {
                            results.success() -> toVersion(File(olderFilePath).nameWithoutExtension).incrementedMinorVersion()
                            else -> null
                        }
                    } catch (e: ContractException) {
                        null
                    }
                }
            }
        }

        if(possibleVersions.isNotEmpty()) {
            println("Recommended version: ${possibleVersions.last().toDisplayableString()}")
            if(possibleVersions.size > 1) {
                println("Available versions: ${possibleVersions.joinToString(", ") { it.toDisplayableString() }}")
            }
        } else {
            val version = "${majorVersionNumbers.last()}.0"
            println("Recommended version: $version")
            println("Not compatible with any of the major versions (${majorVersionNumbers.joinToString(", ") { it.toString() }})")
        }
    }

}