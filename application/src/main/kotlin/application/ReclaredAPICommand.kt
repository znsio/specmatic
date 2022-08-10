package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.Feature
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
        val git = SystemGit()

        val gitRoot = File(git.gitRoot()).canonicalFile
        val contractFile = File(contractFilePath).canonicalFile

        val relativeContractFile = contractFile.relativeTo(gitRoot)

        val newerContractYaml = if(newerVersion.isBlank()) {
            contractFile.readText()
        } else {
            git.show(newerVersion, relativeContractFile.path)
        }

        val newContractPaths = urlPaths(newerContractYaml)

        val newPaths = if(git.exists(olderVersion, relativeContractFile.path)) {
            val olderContractYaml = git.show(olderVersion, relativeContractFile.path)
            val oldContractPaths = urlPaths(olderContractYaml)
            newContractPaths.filter { it !in oldContractPaths }
        } else {
            newContractPaths
        }

        val contracts: List<Pair<Feature, String>> = listOfAllContractFiles(gitRoot).filterNot { it.path == contractFile.path }.map { Pair(OpenApiSpecification.fromYAML(it.readText(), it.path).toFeature(), it.path) }

        val newPathToContract = newPaths.map { newPath ->
            val matchingContracts = contracts.filter { (feature, _) ->
                feature.scenarios.map { it.httpRequestPattern.urlMatcher!!.path }.any { scenarioPath ->
                    scenarioPath == newPath
                }
            }.map { it.second }

            Pair(newPath, matchingContracts)
        }

        newPathToContract.forEach { (newPath, contracts) ->
            println("Path $newPath already exists in the following contracts:")
            println(contracts.joinToString("\n") { "- $it" })
        }
    }

    private fun urlPaths(newerContractYaml: String): List<String> {
        val newContract = OpenApiSpecification.fromYAML(newerContractYaml, "")
        val newContractPaths = newContract.toFeature().scenarios.map { it.httpRequestPattern.urlMatcher!!.path }
        return newContractPaths
    }

    private fun listOfAllContractFiles(dir: File): List<File> {
        val fileGroups = dir.listFiles()!!.groupBy { it.isDirectory }

        val files = (fileGroups[false] ?: emptyList()).map { it.canonicalFile }
        val dirs = (fileGroups[true] ?: emptyList()).filter { it.name != ".git" }.map { it.canonicalFile }

        val dirFiles = dirs.flatMap { listOfAllContractFiles(it) }

        return files.plus(dirFiles)
    }
}
