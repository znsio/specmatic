@file:JvmName("API")
package io.specmatic.stub

import io.specmatic.core.CONTRACT_EXTENSION
import io.specmatic.core.CONTRACT_EXTENSIONS
import io.specmatic.core.CommandHook
import io.specmatic.core.ContractAndStubMismatchMessages
import io.specmatic.core.DATA_DIR_SUFFIX
import io.specmatic.core.Feature
import io.specmatic.core.HookName
import io.specmatic.core.HttpRequest
import io.specmatic.core.MISSING_CONFIG_FILE_MESSAGE
import io.specmatic.core.OPENAPI_FILE_EXTENSIONS
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.WorkingDirectory
import io.specmatic.core.getConfigFilePath
import io.specmatic.core.git.SystemGit
import io.specmatic.core.isContractFile
import io.specmatic.core.loadSpecmaticConfigOrDefault
import io.specmatic.core.log.StringLog
import io.specmatic.core.log.consoleLog
import io.specmatic.core.log.logger
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.parseGherkinStringToFeature
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.core.utilities.ContractPathData.Companion.specToPortMap
import io.specmatic.core.utilities.contractStubPaths
import io.specmatic.core.utilities.examplesDirFor
import io.specmatic.core.utilities.exitWithMessage
import io.specmatic.core.utilities.runWithTimeout
import io.specmatic.mock.NoMatchingScenario
import io.specmatic.mock.ScenarioStub
import org.yaml.snakeyaml.Yaml
import java.io.File

private const val HTTP_STUB_SHUTDOWN_TIMEOUT = 2000L
private const val STUB_START_TIMEOUT = 20_000L

// Used by stub client code
fun createStubFromContractAndData(contractGherkin: String, dataDirectory: String, host: String = "localhost", port: Int = 9000): ContractStub {
    val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

    val mocks = (File(dataDirectory).listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()).map { file ->
        consoleLog(StringLog("Loading data from ${file.name}"))

        ScenarioStub.readFromFile(file)
                .also {
                    contractBehaviour.matchingStub(it, ContractAndStubMismatchMessages)
                }
    }

    return HttpStub(
        contractBehaviour,
        mocks,
        host,
        port,
        ::consoleLog,
        specToStubPortMap = mapOf(contractBehaviour.specification.orEmpty() to port)
    )
}

// Used by stub client code
fun allContractsFromDirectory(dirContainingContracts: String): List<String> =
    File(dirContainingContracts).listFiles()?.filter { it.extension == CONTRACT_EXTENSION }?.map { it.absolutePath } ?: emptyList()

fun createStub(host: String = "localhost", port: Int = 9000): ContractStub {
    return createStub(host, port, false)
}

fun createStub(host: String = "localhost", port: Int = 9000, strict: Boolean = false): ContractStub {
    return createStub(host, port, timeoutMillis = HTTP_STUB_SHUTDOWN_TIMEOUT, strict)
}

// Used by stub client code
fun createStub(
    dataDirPaths: List<String>,
    host: String = "localhost",
    port: Int = 9000
): ContractStub {
    return createStub(dataDirPaths, host, port, false)
}

fun createStub(
    dataDirPaths: List<String>,
    host: String = "localhost",
    port: Int = 9000,
    strict: Boolean = false
): ContractStub {
    return createStub(dataDirPaths, host, port, timeoutMillis = HTTP_STUB_SHUTDOWN_TIMEOUT, strict = strict)
}

fun createStubFromContracts(
    contractPaths: List<String>,
    dataDirPaths: List<String>,
    host: String = "localhost",
    port: Int = 9000
): ContractStub {
    return createStubFromContracts(
        contractPaths,
        dataDirPaths,
        host,
        port,
        timeoutMillis = HTTP_STUB_SHUTDOWN_TIMEOUT
    )
}

// Used by stub client code
fun createStubFromContracts(contractPaths: List<String>, host: String = "localhost", port: Int = 9000): ContractStub {
    return createStubFromContracts(
        contractPaths,
        host,
        port,
        timeoutMillis = HTTP_STUB_SHUTDOWN_TIMEOUT
    )
}

internal fun createStub(
    dataDirPaths: List<String>,
    host: String = "localhost",
    port: Int = 9000,
    timeoutMillis: Long,
    strict: Boolean = false
): ContractStub {
    return createStub(host, port, timeoutMillis, strict, null, dataDirPaths)
}

internal fun createStub(
    host: String = "localhost",
    port: Int = 9000,
    timeoutMillis: Long,
    strict: Boolean = false,
    givenConfigFileName: String? = null,
    dataDirPaths: List<String> = emptyList()
): ContractStub {
    val configFileName = givenConfigFileName ?: getConfigFilePath()
    val specmaticConfig = loadSpecmaticConfigOrDefault(configFileName)

    val stubValues = runWithTimeout(specmaticConfig.getStubStartTimeoutInMilliseconds()) {
        val workingDirectory = WorkingDirectory()
        if (File(configFileName).exists().not()) exitWithMessage(MISSING_CONFIG_FILE_MESSAGE)

        val contractStubPaths = contractStubPaths(configFileName)

        val stubs = if (dataDirPaths.isEmpty()) {
            loadContractStubsFromImplicitPaths(contractStubPaths, specmaticConfig, dataDirPaths)
        } else {
            loadContractStubsFromFiles(contractStubPaths, dataDirPaths, specmaticConfig, strict).plus(
                loadContractStubsFromImplicitPaths(contractStubPaths, specmaticConfig, dataDirPaths)
            )
        }
        val features = stubs.map { it.first }
        val expectations = contractInfoToHttpExpectations(stubs)

        object {
            val workingDirectory = workingDirectory
            val features = features
            val expectations = expectations
            val contractStubPaths = contractStubPaths
        }
    }

    return HttpStub(
        stubValues.features,
        stubValues.expectations,
        host,
        port,
        log = ::consoleLog,
        workingDirectory = stubValues.workingDirectory,
        specmaticConfigPath = File(configFileName).canonicalPath,
        timeoutMillis = timeoutMillis,
        strictMode = strict,
        specToStubPortMap = stubValues.contractStubPaths.specToPortMap()
    )
}

internal fun createStubFromContracts(
    contractPaths: List<String>,
    dataDirPaths: List<String>,
    host: String = "localhost",
    port: Int = 9000,
    timeoutMillis: Long,
    specmaticConfigPath: String? = null
): HttpStub {

    return createStubFromContracts(
        contractPaths,
        dataDirPaths,
        host,
        port,
        timeoutMillis,
        loadSpecmaticConfigOrDefault(specmaticConfigPath)
    )
}

internal fun createStubFromContracts(
    contractPaths: List<String>,
    dataDirPaths: List<String>,
    host: String = "localhost",
    port: Int = 9000,
    timeoutMillis: Long,
    specmaticConfig: SpecmaticConfig
): HttpStub {
    val contractPathData = contractPaths.map { ContractPathData("", it) }
    val contractInfo = loadContractStubsFromFiles(contractPathData, dataDirPaths, specmaticConfig)
    val features = contractInfo.map { it.first }
    val httpExpectations = contractInfoToHttpExpectations(contractInfo)

    return HttpStub(
        features,
        httpExpectations,
        host,
        port,
        ::consoleLog,
        specmaticConfigPath = File(getConfigFilePath()).canonicalPath,
        timeoutMillis = timeoutMillis,
        specToStubPortMap = contractPathData.specToPortMap()
    )
}

internal fun createStubFromContracts(
    contractPaths: List<String>,
    host: String = "localhost",
    port: Int = 9000,
    timeoutMillis: Long
): ContractStub {
    val defaultImplicitDirs: List<String> = implicitContractDataDirs(contractPaths)

    val completeList = if(customImplicitStubBase() != null) {
        defaultImplicitDirs.plus(implicitContractDataDirs(contractPaths, customImplicitStubBase()))
    } else
        defaultImplicitDirs

    return createStubFromContracts(contractPaths, completeList, host, port, timeoutMillis)
}

fun loadContractStubsFromImplicitPaths(
    contractPathDataList: List<ContractPathData>,
    specmaticConfig: SpecmaticConfig = SpecmaticConfig(),
    externalDataDirPaths: List<String>
): List<Pair<Feature, List<ScenarioStub>>> {
    return contractPathDataList.map { Pair(File(it.path), it) }.flatMap { (contractPath, contractSource) ->
        when {
            contractPath.isFile && contractPath.extension in CONTRACT_EXTENSIONS -> {
                consoleLog(StringLog("Loading $contractPath"))

                if(hasOpenApiFileExtension(contractPath.path) && !isOpenAPI(contractPath.path.trim())) {
                    logger.log("Ignoring ${contractPath.path} as it is not an OpenAPI specification")
                    emptyList()
                }
                else try {
                    val implicitDataDirs = implicitDirsForSpecifications(contractPath)
                    val externalDataDirs = dataDirFiles(externalDataDirPaths)

                    val feature = parseContractFileToFeature(
                        contractPath,
                        CommandHook(HookName.stub_load_contract),
                        contractSource.provider,
                        contractSource.repository,
                        contractSource.branch,
                        contractSource.specificationPath,
                        specmaticConfig = specmaticConfig
                    )

                    val stubData = when {
                        implicitDataDirs.any { it.isDirectory } -> {
                            implicitDataDirs.filter { it.isDirectory }.flatMap { implicitDataDir ->
                                consoleLog("Loading stub expectations from ${implicitDataDir.path}".prependIndent("  "))
                                logIgnoredFiles(implicitDataDir)

                                val stubDataFiles = filesInDir(implicitDataDir)?.toList()?.filter { it.extension == "json" }.orEmpty()
                                printDataFiles(stubDataFiles)

                                stubDataFiles.mapNotNull {
                                    try {
                                        Pair(it.path, ScenarioStub.readFromFile(it))
                                    } catch (e: Throwable) {
                                        logger.log(e, "Could not load stub file ${it.canonicalPath}...")
                                        null
                                    }
                                }
                            }
                        }
                        else -> emptyList()
                    }

                    val implicitExampleDirs = stubData.map { File(it.first) }
                    loadContractStubs(
                        features = listOf(
                            Pair(
                                contractPath.path,
                                feature.overrideInlineExamples(
                                    (implicitExampleDirs + externalDataDirs).map {
                                        it.nameWithoutExtension
                                    }.toSet()
                                )
                            )
                        ),
                        stubData = stubData,
                        logIgnoredFiles = true
                    )
                } catch(e: Throwable) {
                    logger.log("Could not load ${contractPath.canonicalPath}")
                    logger.log(e)
                    emptyList()
                }
            }
            contractPath.isDirectory -> {
                loadContractStubsFromImplicitPaths(
                    contractPathDataList = contractPath.listFiles()?.toList()?.map {
                        ContractPathData("",  it.absolutePath)
                    } ?: emptyList(),
                    specmaticConfig = specmaticConfig,
                    externalDataDirPaths = externalDataDirPaths
                )
            }
            else -> emptyList()
        }
    }
}

fun implicitDirsForSpecifications(contractPath: File) =
    listOf(implicitContractDataDir(contractPath.path)).plus(
        if (customImplicitStubBase() != null) listOf(
            implicitContractDataDir(contractPath.path, customImplicitStubBase())
        ) else emptyList()
    ).sorted()

fun hasOpenApiFileExtension(contractPath: String): Boolean =
    OPENAPI_FILE_EXTENSIONS.any { contractPath.trim().endsWith(".$it") }

private fun logIgnoredFiles(implicitDataDir: File) {
    val ignoredFiles = implicitDataDir.listFiles()?.toList()?.filter { it.extension != "json" }?.filter { it.isFile } ?: emptyList()
    if (ignoredFiles.isNotEmpty()) {
        consoleLog(StringLog("Ignoring the following files:".prependIndent("  ")))
        for (file in ignoredFiles) {
            consoleLog(StringLog(file.absolutePath.prependIndent("    ")))
        }
    }
}

fun loadContractStubsFromFiles(
    contractPathDataList: List<ContractPathData>,
    dataDirPaths: List<String>,
    specmaticConfig: SpecmaticConfig,
    strictMode: Boolean = false
): List<Pair<Feature, List<ScenarioStub>>> {
    val contactPathsString = contractPathDataList.joinToString(System.lineSeparator()) { it.path }
    consoleLog(StringLog("Loading the following spec paths:${System.lineSeparator()}$contactPathsString"))
    consoleLog(StringLog(""))

    val invalidContractPaths = contractPathDataList.filter { File(it.path).exists().not() }.map { it.path }
    if (invalidContractPaths.isNotEmpty() && strictMode) {
        val exitMessage = "Error loading the following contracts since they do not exist:${System.lineSeparator()}${
            invalidContractPaths.joinToString(System.lineSeparator())
        }"
        throw Exception(exitMessage)
    }

    val features = contractPathDataList.mapNotNull { contractPathData ->
        loadIfOpenAPISpecification(contractPathData, specmaticConfig)
    }.overrideInlineExamplesWithSameNameFrom(
        dataDirFiles(dataDirPaths)
    )

    logger.debug("Loading stubs from the example directories: '${dataDirPaths.joinToString(", ")}'")
    return loadImplicitExpectationsFromDataDirsForFeature(
        features,
        dataDirPaths,
        specmaticConfig,
        strictMode,
        contractPathDataList
    ).ifEmpty {
        loadExpectationsForFeatures(
            features,
            dataDirPaths,
            strictMode
        )
    }
}

fun loadExpectationsForFeatures(
    features: List<Pair<String, Feature>>,
    dataDirPaths: List<String>,
    strictMode: Boolean = false
): List<Pair<Feature, List<ScenarioStub>>> {
    logger.debug("Scanning the example directories: ${dataDirPaths.joinToString(", ")}...")

    val dataFiles = dataDirFiles(dataDirPaths)
    if(dataFiles.isEmpty()) {
        logger.debug("No example directories/files found within '${dataDirPaths.joinToString(", ")}' OR '${dataDirPaths.joinToString(", ")}' don't exist, hence the example loading operation is skipped for these.")
    }

    logger.newLine()
    logger.log("Loading stub expectations for specs: ${features.joinToString(",") { it.first }}")
    printDataFiles(dataFiles)

    val mockData = dataFiles.mapNotNull {
        try {
            Pair(it.path, ScenarioStub.readFromFile(it))
        } catch (e: Throwable) {
            logger.log(e, "    Could not load stub file ${it.canonicalPath}")
            null
        }
    }

    return loadContractStubs(features, mockData, strictMode)
}

private fun  List<Pair<String, Feature>>.overrideInlineExamplesWithSameNameFrom(dataFiles: List<File>): List<Pair<String, Feature>> {
    val externalExampleNames = dataFiles.map { it.nameWithoutExtension }.toSet()
    return this.map {
        val (_, feature) = it
        it.copy(
            second = feature.overrideInlineExamples(externalExampleNames)
        )
    }
}

private fun dataDirFiles(
    dataDirPaths: List<String>
): List<File> {
    return allDirsInTree(dataDirPaths).flatMap {
        logIgnoredFiles(it)
        it.listFiles()?.toList()?.sorted() ?: emptyList<File>()
    }.filter { it.extension == "json" }
}

fun loadImplicitExpectationsFromDataDirsForFeature(
    features: List<Pair<String, Feature>>,
    dataDirPaths: List<String>,
    specmaticConfig: SpecmaticConfig,
    strictMode: Boolean = false,
    contractPathDataList: List<ContractPathData> = emptyList()
): List<Pair<Feature, List<ScenarioStub>>> {
    return specPathToImplicitDataDirPaths(specmaticConfig, dataDirPaths, contractPathDataList).flatMap { (specPath, implicitOriginalDataDirPairList) ->
        val associatedFeature = features.firstOrNull { (specPathAssociatedToFeature, _) ->
            File(specPathAssociatedToFeature).canonicalPath == File(specPath).canonicalPath
        }

        if(associatedFeature == null) {
            logger.debug("Skipping the loading of examples for '$specPath' as it is not a OpenAPI specification")
            return@flatMap emptyList()
        }

        implicitOriginalDataDirPairList.flatMap { (implicitDataDir, originalDataDir) ->
            val implicitStubs = loadExpectationsForFeatures(
                features = listOf(associatedFeature),
                dataDirPaths = listOf(implicitDataDir),
                strictMode = strictMode
            )
            if(implicitStubs.all { (_, stubs) -> stubs.isEmpty() }) {
                loadExpectationsForFeatures(
                    listOf(associatedFeature),
                    listOf(originalDataDir),
                    strictMode
                )
            } else {
                logger.debug("Successfully loaded stub expectations for $specPath in $implicitDataDir${System.lineSeparator()}")
                implicitStubs
            }
        }
    }
}

private fun specPathToImplicitDataDirPaths(
    specmaticConfig: SpecmaticConfig,
    dataDirPaths: List<String>,
    contractPathDataList: List<ContractPathData>
): List<Pair<String, List<ImplicitOriginalDataDirPair>>> {
    return specmaticConfig.loadSources().flatMap { contractSource ->
        contractSource.stubDirectoryToContractPath(contractPathDataList)
    }.mapNotNull { (stubDirectory, stubContractPath) ->
        if (stubContractPath.isContractFile().not()) {
            return@mapNotNull null
        }

        val implicitExamplesPath = stubContractPath.substringBeforeLast(".").plus("_examples")

        val stubContractPathWithDirectory =
            if (stubDirectory.isNotBlank()) "$stubDirectory${File.separator}$stubContractPath" else stubContractPath
        stubContractPathWithDirectory to dataDirPaths.map { dataDirPath ->
            ImplicitOriginalDataDirPair(
                implicitDataDir = "$dataDirPath${File.separator}$implicitExamplesPath",
                dataDir = dataDirPath
            )
        }
    }
}

data class ImplicitOriginalDataDirPair(
    val implicitDataDir: String,
    val dataDir: String
)

private fun printDataFiles(dataFiles: List<File>) {
    if (dataFiles.isNotEmpty()) {
        val dataFilesString = dataFiles.joinToString(System.lineSeparator()) { it.path.prependIndent("  ") }
        consoleLog(StringLog("Scanning the following to find the matching stub files:${System.lineSeparator()}$dataFilesString".prependIndent("  ")))
    }
}

class StubMatchExceptionReport(val request: HttpRequest, val e: NoMatchingScenario) {
    fun withoutFluff(fluffLevel: Int): StubMatchExceptionReport {
        return StubMatchExceptionReport(request, e.withoutFluff(fluffLevel))
    }

    fun hasErrors(): Boolean {
        return e.results.hasResults()
    }

    val message: String
        get() = e.report(request)
}

data class StubMatchErrorReport(val exceptionReport: StubMatchExceptionReport, val contractFilePath: String) {
    fun withoutFluff(fluffLevel: Int): StubMatchErrorReport {
        return this.copy(exceptionReport = exceptionReport.withoutFluff(fluffLevel))
    }

    fun hasErrors(): Boolean {
        return exceptionReport.hasErrors()
    }
}
data class StubMatchResults(val feature: Feature?, val errorReport: StubMatchErrorReport?)

fun stubMatchErrorMessage(
    matchResults: List<StubMatchResults>,
    stubFile: String,
    specs: List<String> = emptyList()
): String {
    val matchResultsWithErrorReports = matchResults.mapNotNull { it.errorReport }

    val errorReports: List<StubMatchErrorReport> = matchResultsWithErrorReports.map {
        it.withoutFluff(0)
    }.filter {
        it.hasErrors()
    }.ifEmpty {
        matchResultsWithErrorReports.map {
            it.withoutFluff(1)
        }.filter {
            it.hasErrors()
        }
    }

    if(errorReports.isEmpty() || matchResults.isEmpty())
        return "$stubFile didn't match any of the contracts from ${specs.joinToString(", ")}\n${matchResults.firstOrNull()?.errorReport?.exceptionReport?.request?.requestNotRecognized()?.prependIndent("  ")}".trim()



    return errorReports.joinToString("${System.lineSeparator()}${System.lineSeparator()}") { (exceptionReport, contractFilePath) ->
        "$stubFile didn't match $contractFilePath${System.lineSeparator()}${
            exceptionReport.message.prependIndent(
                "  "
            )
        }"
    }
}

fun loadContractStubs(
    features: List<Pair<String, Feature>>,
    stubData: List<Pair<String, ScenarioStub>>,
    strictMode: Boolean = false,
    logIgnoredFiles: Boolean = false
): List<Pair<Feature, List<ScenarioStub>>> {
    val contractInfoFromStubs: List<Pair<Feature, List<ScenarioStub>>> = stubData.flatMap { (stubFile, stub) ->
        val matchResults = features.map { (specFile, feature) ->
            try {
                feature.matchingStub(stub, ContractAndStubMismatchMessages)
                StubMatchResults(feature, null)
            } catch (e: NoMatchingScenario) {
                StubMatchResults(null, StubMatchErrorReport(StubMatchExceptionReport(stub.partial?.request ?: stub.request, e), specFile))
            }
        }

        if(matchResults.all { it.feature == null }) {
            val specs = features.map { it.first }
            val errorMessage = stubMatchErrorMessage(matchResults, stubFile, specs).prependIndent("  ")
            if(strictMode) throw Exception(errorMessage)
            else {
                val message = "No matching spec found for the stub expectation in '${stubFile}':${System.lineSeparator()} $errorMessage"
                if (logIgnoredFiles) logger.log(message)
                logger.debug("${System.lineSeparator()}$message")
            }
            return@flatMap emptyList()
        }

        matchResults.mapNotNull { matchResult ->
            if (matchResult.feature == null) {
                null
            } else {
                logger.debug("Successfully loaded the stub expectation from '${stub.filePath.orEmpty()} for ${matchResult.feature.path}'")
                Pair(matchResult.feature, stub)
            }
        }
    }.groupBy { it.first }.mapValues { (_, value) -> value.map { it.second } }.entries.map { Pair(it.key, it.value) }

    val stubbedFeatures = contractInfoFromStubs.map { it.first }
    val missingFeatures = features.map { it.second }.filter { it !in stubbedFeatures }

    return contractInfoFromStubs.plus(missingFeatures.map { Pair(it, emptyList()) })
}

fun allDirsInTree(
    dataDirPaths: List<String>
): List<File> =
        dataDirPaths.map { File(it) }.filter {
            it.exists() && it.isDirectory
        }.flatMap {
            val fileList: List<File> = it.listFiles()?.toList()?.filterNotNull() ?: emptyList()
            pathToFileListRecursive(fileList).plus(it)
        }

private fun pathToFileListRecursive(
    dataDirFiles: List<File>
): List<File> =
        dataDirFiles.filter {
            it.isDirectory
        }.map {
            val fileList: List<File> = it.listFiles()?.toList()?.filterNotNull() ?: emptyList()
            pathToFileListRecursive(fileList).plus(it)
        }.flatten()

private fun filesInDir(implicitDataDir: File): List<File>? {
    val files = implicitDataDir.listFiles()?.map {
        when {
            it.isDirectory -> {
                filesInDir(it) ?: emptyList()
            }
            it.isFile -> {
                listOf(it)
            }
            else -> {
                logger.debug("Could not recognise ${it.absolutePath}, ignoring it.")
                emptyList()
            }
        }
    }

    return files?.flatten()
}


fun implicitContractDataDirs(contractPaths: List<String>, customBase: String? = null) =
        contractPaths.map { implicitContractDataDir(it, customBase).absolutePath }

fun customImplicitStubBase(): String? = System.getenv("SPECMATIC_CUSTOM_IMPLICIT_STUB_BASE") ?: System.getProperty("customImplicitStubBase")

fun implicitContractDataDir(contractPath: String, customBase: String? = null): File {
    val contractFile = File(contractPath)

    return if(customBase == null)
        examplesDirFor("${contractFile.absoluteFile.parent}/${contractFile.name}", DATA_DIR_SUFFIX)
    else {
        val gitRoot: String = File(SystemGit().inGitRootOf(contractPath).workingDirectory).canonicalPath
        val fullContractPath = File(contractPath).canonicalPath

        val relativeContractPath = File(fullContractPath).relativeTo(File(gitRoot))
        File(gitRoot).resolve(customBase).resolve(relativeContractPath).let {
            examplesDirFor("${it.parent}/${it.name}", DATA_DIR_SUFFIX)
        }
    }
}

fun loadIfOpenAPISpecification(contractPathData: ContractPathData, specmaticConfig: SpecmaticConfig): Pair<String, Feature>? {
    if (recognizedExtensionButNotOpenAPI(contractPathData) || isOpenAPI(contractPathData.path))
        return Pair(contractPathData.path, parseContractFileToFeature(contractPathData.path, CommandHook(HookName.stub_load_contract), contractPathData.provider, contractPathData.repository, contractPathData.branch, contractPathData.specificationPath).copy(specmaticConfig = specmaticConfig))

    logger.log("Ignoring ${contractPathData.path} as it does not have a recognized specification extension")
    return null
}

private fun recognizedExtensionButNotOpenAPI(contractPathData: ContractPathData) =
    !hasOpenApiFileExtension(contractPathData.path) && File(contractPathData.path).extension in CONTRACT_EXTENSIONS

fun isOpenAPI(path: String): Boolean =
    try {
        Yaml().load<MutableMap<String, Any?>>(File(path).reader()).contains("openapi")
    } catch(e: Throwable) {
        logger.log(e, "Could not parse $path")
        false
    }
