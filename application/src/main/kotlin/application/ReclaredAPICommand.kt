package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.Feature
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.log.LogMessage
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.Callable

fun fetchAllContracts(directory: String): List<Pair<Feature, String>> =
    listOfAllContractFiles(File(directory)).mapNotNull {
        loadContractData(it)
    }

fun loadContractData(it: File) = try {
    Pair(OpenApiSpecification.fromYAML(it.readText(), it.path).toFeature(), it.path)
} catch (e: Throwable) {
    logger.debug(exceptionCauseMessage(e))
    null
}

@CommandLine.Command(name = "redeclared",
    mixinStandardHelpOptions = true,
    description = ["Checks if new APIs in this file have been re-declared"])
class ReDeclaredAPICommand: Callable<Unit> {
    @CommandLine.Command(name = "file", description = ["Check the specified contract for re-declarations"])
    fun file(@CommandLine.Parameters(paramLabel = "contractPath") contractFilePath: String): Int {
        val redeclarations = findReDeclaredContracts(ContractToCheck(contractFilePath, SystemGit()))

        if(redeclarations.isNotEmpty()) {
            logger.log("Some APIs in $contractFilePath have been declared in other files as well.")
            logger.newLine()
        }

        redeclarations.forEach { (newPath, contracts) ->
            logger.log(newPath)
            logger.log(contracts.joinToString("\n") { "- $it" })
        }

        return if(redeclarations.isNotEmpty())
            1
        else
            0
    }

    class JSONArrayLogMessage(val json: JSONArrayValue): LogMessage {
        override fun toJSONObject(): JSONObjectValue {
            return JSONObjectValue(mapOf("list" to json))
        }

        override fun toLogString(): String {
            return json.displayableValue()
        }

    }

    @CommandLine.Command(name = "entire-repo", description = ["Check all contracts in the repo for re-declarations"])
    fun entireRepo(@Option(names = ["--json"]) json: Boolean, @Option(names = ["--baseDirectory"]) suppliedBaseDirectory: String? = null, @Option(names = ["--systemLevel"]) systemLevel: Int = 0, @Option(names = ["--ignoreAPI"]) ignoreAPIs: List<String>? = emptyList()): Int {
        val baseDirectory = suppliedBaseDirectory ?: SystemGit().gitRoot()
        val contracts: List<Pair<Feature, String>> = fetchAllContracts(baseDirectory)

        val ignorableAPIs = ignoreAPIs ?: emptyList()

        val reDeclarations: Map<String, List<String>> = findReDeclarationsAmongstContracts(contracts, baseDirectory, systemLevel).filterKeys {
            it !in ignorableAPIs
        }

        logRedeclarations(json, reDeclarations)

        return if(reDeclarations.isNotEmpty())
            1
        else
            0
    }

    @CommandLine.Command(name = "branch", description = ["Check all new or updated contracts in the branch for re-declarations"])
    fun branch(@Option(names = ["--json"]) json: Boolean, @Option(names = ["--main-branch"], defaultValue = "master") mainBranch: String): Int {
        val relativePaths = SystemGit().getChangesFromMainBranch(mainBranch).filter { File(it).exists() }.filter { it.endsWith("yaml") }
        val reDeclarations = relativePaths.flatMap {
            findReDeclaredContracts(ContractToCheck(it, SystemGit()))
        }.groupBy {
            it.apiURLPath
        }.mapValues {
            it.value.flatMap { it.contractsContainingAPI }.distinct()
        }.filter {
            it.value.size > 1
        }

        logRedeclarations(json, reDeclarations)

        return if(reDeclarations.isNotEmpty())
            1
        else
            0
    }

    private fun logRedeclarations(
        json: Boolean,
        reDeclarations: Map<String, List<String>>
    ) {
        val sorted = reDeclarations.entries.sortedBy { (api, _) ->
            api
        }

        if (json) {
            printJSON(sorted)
        } else {
            printText(reDeclarations, sorted)
        }
    }

    private fun printText(
        reDeclarations: Map<String, List<String>>,
        sorted: List<Map.Entry<String, List<String>>>
    ) {
        if (reDeclarations.isNotEmpty()) {
            logger.log("Some APIs have been declared in multiple files.")
            logger.newLine()
        }

        sorted.forEach { (newPath, contracts) ->
            logger.log(newPath)
            logger.log(contracts.joinToString("\n"))
            if(contracts.map { File(it).readText() }.distinct().size == 1)
                logger.log("NOTE: These files are exact duplicates")
            logger.newLine()
        }

        logger.log("Count of APIs re-declared: ${reDeclarations.size}")
    }

    private fun printJSON(sorted: List<Map.Entry<String, List<String>>>) {
        val reDeclarationsJSON = JSONArrayValue(sorted.map { (api, files) ->
            val jsonFileList = JSONArrayValue(files.map { StringValue(it) })
            JSONObjectValue(mapOf("api" to StringValue(api), "files" to jsonFileList))
        })

        logger.log(JSONArrayLogMessage(reDeclarationsJSON))
    }

    override fun call() {
        CommandLine(GitCompatibleCommand()).usage(System.out)
    }
}

data class APIReDeclarations(val apiURLPath: String, val contractsContainingAPI: List<String>)

fun findReDeclarationsAmongstContracts(contracts: List<Pair<Feature, String>>, baseDirectory: String = "", systemLevel: Int = 0): Map<String, List<String>> {
    val declarations = contracts.flatMap { (feature, filePath) ->
        pathsFromFeature(feature).map { urlPath -> Pair(urlPath, filePath) }
    }.groupBy { (urlPath, _) -> urlPath }.mapValues { (_, value) ->
        value.map { (_, path) -> path }
    }

    val multipleDeclarations = declarations.filter { (_, filePaths) -> filePaths.size > 1 }.let { reDeclarations ->
        if(systemLevel > 0) {
            val canonicalBase = File(baseDirectory).canonicalFile

            reDeclarations.filterValues { paths ->
                val distinctPathLevels: List<String> = paths.map {
                    val relativePathParts = File(it).canonicalFile.parentFile.relativeTo(canonicalBase).path.removePrefix("/").split("/")

                    (0 until systemLevel).mapNotNull { level ->
                        relativePathParts.getOrNull(level)
                    }.joinToString("/")
                }.distinct()

                distinctPathLevels.size == 1
            }
        } else {
            reDeclarations
        }
    }

    return multipleDeclarations
}

fun findReDeclaredContracts(
    contractToCheck: ContractToCheck,
): List<APIReDeclarations> {
    return try {
        val paths: List<String> = contractToCheck.getPathsInContract() ?: emptyList()
        val contracts: List<Pair<Feature, String>> = contractToCheck.fetchAllOtherContracts()

        findRedeclarations(paths, contracts)
    } catch(e: Throwable) {
        logger.log("Unhandled exception caught when parsing contract contra${contractToCheck.path}")
        emptyList()
    }
}

fun findRedeclarations(
    newPaths: List<String>,
    contracts: List<Pair<Feature, String>>
): List<APIReDeclarations> {
    val newPathToContractMap = newPaths.map { newPath ->
        val matchingContracts = contracts.filter { (feature, _) ->
            feature.scenarios.map { it.httpRequestPattern.urlMatcher!!.path }.any { scenarioPath ->
                scenarioPath == newPath
            }
        }.map { it.second }

        APIReDeclarations(newPath, matchingContracts)
    }

    return newPathToContractMap
}

fun urlPaths(newerContractYaml: String, contractPath: String): List<String>? {
    return try {
        val specification = OpenApiSpecification.fromYAML(newerContractYaml, contractPath)
        if(specification.isOpenAPI31()) {
            logger.log("$contractPath is written using OpenAPI 3.1, which is not yet supported")
            return emptyList()
        }

        val newContract = specification.toFeature()
        pathsFromFeature(newContract)
    } catch(e: ContractException) {
        logger.debug(exceptionCauseMessage(e))
        null
    }
}

private fun pathsFromFeature(newContract: Feature) =
    newContract.scenarios.map { it.httpRequestPattern.urlMatcher!!.path }.sorted().distinct()

open class CanonicalFile(val file: File) {
    val path: String = file.path

    constructor (path: String) : this(File(path).canonicalFile)
    fun readText(): String = file.readText()
    fun relativeTo(parentDir: File): File = file.relativeTo(parentDir)
}

fun listOfAllContractFiles(dir: File): List<File> {
    val fileGroups = dir.listFiles()!!.groupBy { it.isDirectory }

    val files = (fileGroups[false] ?: emptyList()).filter { it.extension == "yaml" }.map { it.canonicalFile }
    val dirs = (fileGroups[true] ?: emptyList()).filter { it.name != ".git" }.map { it.canonicalFile }

    val dirFiles = dirs.flatMap { listOfAllContractFiles(it) }

    return files.plus(dirFiles)
}
