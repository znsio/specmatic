package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.Feature
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.SystemGit
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "redeclared",
    mixinStandardHelpOptions = true,
    description = ["Checks if new APIs in this file have been re-declared"])
class ReDeclaredAPICommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", description = ["Contract to validate"])
    lateinit var contractFilePath: String

    @CommandLine.Option(names = ["--older"], description = ["Older version"], defaultValue = "HEAD")
    lateinit var olderVersion: String

    @CommandLine.Option(names = ["--newer"], description = ["Newer version"], defaultValue = "")
    lateinit var newerVersion: String

    override fun call() {
        val contractFile = CanonicalFile(contractFilePath)

        val newPaths = getNewPaths(contractFile, olderVersion, newerVersion, SystemGit())
        val contracts: List<Pair<Feature, String>> = fetchAllContracts(contractFile, SystemGit())

        val newPathToContractMap = newPathToContractMap(newPaths, contracts)

        newPathToContractMap.forEach { (newPath, contracts) ->
            println("Path $newPath already exists in the following contracts:")
            println(contracts.joinToString("\n") { "- $it" })
        }
    }
}

private fun newPathToContractMap(
    newPaths: List<String>,
    contracts: List<Pair<Feature, String>>
): List<Pair<String, List<String>>> {
    val newPathToContractMap = newPaths.map { newPath ->
        val matchingContracts = contracts.filter { (feature, _) ->
            feature.scenarios.map { it.httpRequestPattern.urlMatcher!!.path }.any { scenarioPath ->
                scenarioPath == newPath
            }
        }.map { it.second }

        Pair(newPath, matchingContracts)
    }
    return newPathToContractMap
}

private fun fetchAllContracts(
    contractFile: CanonicalFile,
    git: GitCommand
) =
    listOfAllContractFiles(File(git.gitRoot())).filterNot { it.path == contractFile.path }
        .map { Pair(OpenApiSpecification.fromYAML(it.readText(), it.path).toFeature(), it.path) }

fun getNewPaths(
    contractFile: CanonicalFile,
    olderVersion: String,
    newerVersion: String,
    git: GitCommand
): List<String> {
    val gitRoot = File(git.gitRoot())

    val relativeContractFile = contractFile.relativeTo(gitRoot)

    val newerContractYaml = if (newerVersion.isBlank()) {
        contractFile.readText()
    } else {
        git.show(newerVersion, relativeContractFile.path)
    }

    val newContractPaths = urlPaths(newerContractYaml)

    return if (git.exists(olderVersion, relativeContractFile.path)) {
        val olderContractYaml = git.show(olderVersion, relativeContractFile.path)
        val oldContractPaths = urlPaths(olderContractYaml)
        newContractPaths.filter { it !in oldContractPaths }
    } else {
        newContractPaths
    }
}

fun urlPaths(newerContractYaml: String): List<String> {
    val newContract = OpenApiSpecification.fromYAML(newerContractYaml, "")
    return newContract.toFeature().scenarios.map { it.httpRequestPattern.urlMatcher!!.path }
}

fun listOfAllContractFiles(dir: File): List<File> {
    val fileGroups = dir.listFiles()!!.groupBy { it.isDirectory }

    val files = (fileGroups[false] ?: emptyList()).map { it.canonicalFile }
    val dirs = (fileGroups[true] ?: emptyList()).filter { it.name != ".git" }.map { it.canonicalFile }

    val dirFiles = dirs.flatMap { listOfAllContractFiles(it) }

    return files.plus(dirFiles)
}

open class CanonicalFile(val file: File) {
    val path: String = file.path

    constructor (path: String) : this(File(path).canonicalFile)
    fun readText(): String = file.readText()
    fun relativeTo(parentDir: File): File = file.relativeTo(parentDir)
}