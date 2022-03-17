@file:JvmName("API")
package `in`.specmatic.stub

import `in`.specmatic.core.log.consoleLog
import `in`.specmatic.core.*
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.log.StringLog
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.contractStubPaths
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.value.KafkaMessage
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.mock.*
import java.io.File
import java.time.Duration

// Used by stub client code
fun createStubFromContractAndData(contractGherkin: String, dataDirectory: String, host: String = "localhost", port: Int = 9000): HttpStub {
    val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

    val mocks = (File(dataDirectory).listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()).map { file ->
        consoleLog(StringLog("Loading data from ${file.name}"))

        stringToMockScenario(StringValue(file.readText(Charsets.UTF_8)))
                .also {
                    contractBehaviour.matchingStub(it, ContractAndStubMismatchMessages)
                }
    }

    return HttpStub(contractBehaviour, mocks, host, port, ::consoleLog)
}

// Used by stub client code
fun allContractsFromDirectory(dirContainingContracts: String): List<String> =
    File(dirContainingContracts).listFiles()?.filter { it.extension == CONTRACT_EXTENSION }?.map { it.absolutePath } ?: emptyList()

fun createStub(host: String = "localhost", port: Int = 9000): HttpStub {
    val workingDirectory = WorkingDirectory()
    val contractPaths = contractStubPaths().map { it.path }
    val stubs = loadContractStubsFromImplicitPaths(contractPaths)
    val features = stubs.map { it.first }
    val expectations = contractInfoToHttpExpectations(stubs)

    return HttpStub(features, expectations, host, port, log = ::consoleLog, workingDirectory = workingDirectory)
}

// Used by stub client code
fun createStub(dataDirPaths: List<String>, host: String = "localhost", port: Int = 9000): HttpStub {
    val contractPaths = contractStubPaths().map { it.path }
    val contractInfo = loadContractStubsFromFiles(contractPaths, dataDirPaths)
    val features = contractInfo.map { it.first }
    val httpExpectations = contractInfoToHttpExpectations(contractInfo)

    return HttpStub(features, httpExpectations, host, port, ::consoleLog)
}

fun createStubFromContracts(contractPaths: List<String>, dataDirPaths: List<String>, host: String = "localhost", port: Int = 9000): HttpStub {
    val contractInfo = loadContractStubsFromFiles(contractPaths, dataDirPaths)
    val features = contractInfo.map { it.first }
    val httpExpectations = contractInfoToHttpExpectations(contractInfo)

    return HttpStub(features, httpExpectations, host, port, ::consoleLog)
}

fun loadContractStubsFromImplicitPaths(contractPaths: List<String>): List<Pair<Feature, List<ScenarioStub>>> {
    return contractPaths.map { File(it) }.flatMap { contractPath ->
        when {
            contractPath.isFile && contractPath.extension in CONTRACT_EXTENSIONS -> {
                consoleLog(StringLog("Loading $contractPath"))
                try {
                    val feature = parseContractFileToFeature(contractPath, CommandHook(HookName.stub_load_contract))
                    val implicitDataDir = implicitContractDataDir(contractPath.path)

                    val stubData = when {
                        implicitDataDir.isDirectory -> {
                            consoleLog(StringLog("Loading stub expectations from ${implicitDataDir.path}".prependIndent("  ")))
                            logIgnoredFiles(implicitDataDir)

                            val stubDataFiles =
                                filesInDir(implicitDataDir)?.toList()?.filter { it.extension == "json" } ?: emptyList()
                            printDataFiles(stubDataFiles)
                            stubDataFiles.map {
                                Pair(it.path, stringToMockScenario(StringValue(it.readText())))
                            }
                        }
                        else -> emptyList()
                    }

                    loadContractStubs(listOf(Pair(contractPath.path, feature)), stubData)
                } catch(e: Throwable) {
                    logger.log(e, "Could not load ${contractPath.canonicalPath}")
                    emptyList()
                }
            }
            contractPath.isDirectory -> {
                loadContractStubsFromImplicitPaths(contractPath.listFiles()?.toList()?.map { it.absolutePath } ?: emptyList())
            }
            else -> emptyList()
        }
    }
}

private fun logIgnoredFiles(implicitDataDir: File) {
    val ignoredFiles = implicitDataDir.listFiles()?.toList()?.filter { it.extension != "json" }?.filter { it.isFile } ?: emptyList()
    if (ignoredFiles.isNotEmpty()) {
        consoleLog(StringLog("Ignoring the following files:".prependIndent("  ")))
        for (file in ignoredFiles) {
            consoleLog(StringLog(file.absolutePath.prependIndent("    ")))
        }
    }
}

fun loadContractStubsFromFiles(contractPaths: List<String>, dataDirPaths: List<String>): List<Pair<Feature, List<ScenarioStub>>> {
    val contactPathsString = contractPaths.joinToString(System.lineSeparator())
    consoleLog(StringLog("Loading the following contracts:${System.lineSeparator()}$contactPathsString"))
    consoleLog(StringLog(""))

    val dataDirFileList = allDirsInTree(dataDirPaths)

    val features = contractPaths.map { path ->
        Pair(path, parseContractFileToFeature(path, CommandHook(HookName.stub_load_contract)))
    }

    val dataFiles = dataDirFileList.flatMap {
        consoleLog(StringLog("Loading stub expectations from ${it.path}".prependIndent("  ")))
        logIgnoredFiles(it)
        it.listFiles()?.toList() ?: emptyList<File>()
    }.filter { it.extension == "json" }
    printDataFiles(dataFiles)

    val mockData = dataFiles.map { Pair(it.path, stringToMockScenario(StringValue(it.readText()))) }

    return loadContractStubs(features, mockData)
}

private fun printDataFiles(dataFiles: List<File>) {
    if (dataFiles.isNotEmpty()) {
        val dataFilesString = dataFiles.joinToString(System.lineSeparator()) { it.path.prependIndent("  ") }
        consoleLog(StringLog("Reading the following stub files:${System.lineSeparator()}$dataFilesString".prependIndent("  ")))
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
    stubFile: String
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
        return "$stubFile didn't match any of the contracts\n${matchResults.firstOrNull()?.errorReport?.exceptionReport?.request?.requestNotRecognized()?.prependIndent("  ")}".trim()



    return errorReports.map { (exceptionReport, contractFilePath) ->
        "$stubFile didn't match $contractFilePath${System.lineSeparator()}${
            exceptionReport.message.prependIndent(
                "  "
            )
        }"
    }.joinToString("${System.lineSeparator()}${System.lineSeparator()}")
}

fun loadContractStubs(features: List<Pair<String, Feature>>, stubData: List<Pair<String, ScenarioStub>>): List<Pair<Feature, List<ScenarioStub>>> {
    val contractInfoFromStubs = stubData.mapNotNull { (stubFile, stub) ->
        val matchResults = features.map { (qontractFile, feature) ->
            try {
                val kafkaMessage = stub.kafkaMessage
                if (kafkaMessage != null) {
                    feature.assertMatchesMockKafkaMessage(kafkaMessage)
                } else {
                    feature.matchingStub(stub.request, stub.response, ContractAndStubMismatchMessages)
                }
                StubMatchResults(feature, null)
            } catch (e: NoMatchingScenario) {
                StubMatchResults(null, StubMatchErrorReport(StubMatchExceptionReport(stub.request, e), qontractFile))
            }
        }

        when (val feature = matchResults.mapNotNull { it.feature }.firstOrNull()) {
            null -> {
                val errorMessage = stubMatchErrorMessage(matchResults, stubFile).prependIndent("  ")

                consoleLog(StringLog(errorMessage))

                null
            }
            else -> Pair(feature, stub)
        }
    }.groupBy { it.first }.mapValues { it.value.map { it.second } }.entries.map { Pair(it.key, it.value) }

    val stubbedFeatures = contractInfoFromStubs.map { it.first }
    val missingFeatures = features.map { it.second }.filter { it !in stubbedFeatures }

    return contractInfoFromStubs.plus(missingFeatures.map { Pair(it, emptyList<ScenarioStub>()) })
}

fun allDirsInTree(dataDirPath: String): List<File> = allDirsInTree(listOf(dataDirPath))
fun allDirsInTree(dataDirPaths: List<String>): List<File> =
        dataDirPaths.map { File(it) }.filter {
            it.isDirectory
        }.flatMap {
            val fileList: List<File> = it.listFiles()?.toList()?.filterNotNull() ?: emptyList()
            pathToFileListRecursive(fileList).plus(it)
        }

private fun pathToFileListRecursive(dataDirFiles: List<File>): List<File> =
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

// Used by stub client code
fun createStubFromContracts(contractPaths: List<String>, host: String = "localhost", port: Int = 9000): ContractStub {
    val dataDirPaths = implicitContractDataDirs(contractPaths)
    return createStubFromContracts(contractPaths, dataDirPaths, host, port)
}

fun implicitContractDataDirs(contractPaths: List<String>) =
        contractPaths.map { implicitContractDataDir(it).absolutePath }

val customImplicitStubBase: String? = System.getenv("SPECMATIC_CUSTOM_IMPLICIT_STUB_BASE") ?: System.getProperty("customImplicitStubBase")

fun implicitContractDataDir(contractPath: String): File {
    val contractFile = File(contractPath)
    val customImplicitStubBase: String? = customImplicitStubBase

    return if(customImplicitStubBase == null)
        File("${contractFile.absoluteFile.parent}/${contractFile.nameWithoutExtension}$DATA_DIR_SUFFIX")
    else {
        val gitRoot: String = File(SystemGit().inGitRootOf(contractPath).workingDirectory).canonicalPath
        val fullContractPath = File(contractPath).canonicalPath

        val relativeContractPath = File(fullContractPath).relativeTo(File(gitRoot))
        File(gitRoot).resolve(customImplicitStubBase).resolve(relativeContractPath).let {
            File("${it.parent}/${it.nameWithoutExtension}$DATA_DIR_SUFFIX")
        }
    }
}

// Used by stub client code
fun stubKafkaMessage(contractPath: String, message: String, bootstrapServers: String) {
    val kafkaMessage = kafkaMessageFromJSON(getJSONObjectValue(MOCK_KAFKA_MESSAGE, jsonStringToValueMap(message)))
    parseGherkinStringToFeature(File(contractPath).readText()).assertMatchesMockKafkaMessage(kafkaMessage)
    createProducer(bootstrapServers).use {
        it.send(producerRecord(kafkaMessage))
    }
}

// Used by stub client code
fun testKafkaMessage(contractPath: String, bootstrapServers: String, commit: Boolean) {
    val feature = parseGherkinStringToFeature(File(contractPath).readText())

    val results = feature.scenarios.map {
        testKafkaMessages(it, bootstrapServers, commit)
    }

    if(results.any { it is Result.Failure }) {
        throw ContractException(Results(results.toMutableList()).report(PATH_NOT_RECOGNIZED_ERROR))
    }
}

fun testKafkaMessages(scenario: Scenario, bootstrapServers: String, commit: Boolean): Result {
    return createConsumer(bootstrapServers, commit).use { consumer ->
        if(scenario.kafkaMessagePattern == null) throw ContractException("No kafka message found to test with")

        val topic = scenario.kafkaMessagePattern.topic
        consumer.subscribe(listOf(topic))

        val messages = consumer.poll(Duration.ofSeconds(1)).map {
            KafkaMessage(topic, it.key()?.let { key -> StringValue(key) }, StringValue(it.value()))
        }

        val results = messages.map {
            scenario.kafkaMessagePattern.matches(it, scenario.resolver)
        }

        Results(results.toMutableList()).toResultIfAny()
    }
}
