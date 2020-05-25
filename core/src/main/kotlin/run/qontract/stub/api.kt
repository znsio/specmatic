@file:JvmName("API")
package run.qontract.stub

import run.qontract.consoleLog
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.utilities.readFile
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import run.qontract.mock.*
import java.io.File
import java.time.Duration

fun createStubFromContractAndData(contractGherkin: String, dataDirectory: String, host: String = "localhost", port: Int = 9000): HttpStub {
    val contractBehaviour = ContractBehaviour(contractGherkin)

    val mocks = (File(dataDirectory).listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()).map { file ->
        println("Loading data from ${file.name}")

        stringToMockScenario(StringValue(file.readText(Charsets.UTF_8)))
                .also {
                    contractBehaviour.matchingMockResponse(it)
                }
    }

    return HttpStub(contractBehaviour, mocks, host, port, ::consoleLog)
}

fun allContractsFromDirectory(dirContainingContracts: String): List<String> =
    File(dirContainingContracts).listFiles()?.filter { it.extension == CONTRACT_EXTENSION }?.map { it.absolutePath } ?: emptyList()

fun createStubFromContracts(contractPaths: List<String>, dataDirPaths: List<String>, host: String = "localhost", port: Int = 9000): HttpStub {
    val contractInfo = loadContractStubs(contractPaths, dataDirPaths)
    val behaviours = contractInfo.map { it.first }
    val httpExpectations = contractInfoToHttpExpectations(contractInfo)

    return HttpStub(behaviours, httpExpectations, host, port, ::consoleLog)
}

fun loadContractStubs(contractPaths: List<String>, dataDirPaths: List<String>): List<Pair<ContractBehaviour, List<MockScenario>>> {
    val dataDirFileList = allDirsInTree(dataDirPaths)

    val behaviours = contractPaths.map { path ->
        Pair(File(path), ContractBehaviour(readFile(path)))
    }

    val dataFiles = dataDirFileList.flatMap {
        it.listFiles()?.toList() ?: emptyList<File>()
    }.filter { it.extension == "json" }
    if (dataFiles.isNotEmpty())
        println("Reading the stub files below:${System.lineSeparator()}${dataFiles.joinToString(System.lineSeparator())}")

    val mockData = dataFiles.map { Pair(it, stringToMockScenario(StringValue(it.readText()))) }

    val contractInfoFromMocks = mockData.mapNotNull { (mockFile, mock) ->
        val matchResults = behaviours.asSequence().map { (contractFile, behaviour) ->
            try {
                val kafkaMessage = mock.kafkaMessage
                if (kafkaMessage != null) {
                    behaviour.assertMatchesMockKafkaMessage(kafkaMessage)
                } else {
                    behaviour.matchingMockResponse(mock.request, mock.response)
                }
                Pair(behaviour, null)
            } catch (e: NoMatchingScenario) {
                Pair(null, Pair(e, contractFile))
            }
        }

        when (val behaviour = matchResults.mapNotNull { it.first }.firstOrNull()) {
            null -> {
                println(matchResults.mapNotNull { it.second }.map { (exception, contractFile) ->
                    "${mockFile.absolutePath} didn't match ${contractFile.absolutePath}${System.lineSeparator()}${exception.message}"
                }.joinToString("${System.lineSeparator()}${System.lineSeparator()}"))
                null
            }
            else -> Pair(behaviour, mock)
        }
    }.groupBy { it.first }.mapValues { it.value.map { it.second } }.entries.map { Pair(it.key, it.value) }

    val mockedBehaviours = contractInfoFromMocks.map { it.first }
    val missingBehaviours = behaviours.map { it.second }.filter { it !in mockedBehaviours }

    val contractInfo = contractInfoFromMocks.plus(missingBehaviours.map { Pair(it, emptyList<MockScenario>()) })
    return contractInfo
}

fun allDirsInTree(dataDirPath: String): List<File> = allDirsInTree(listOf(dataDirPath))
fun allDirsInTree(dataDirPaths: List<String>): List<File> =
        dataDirPaths.map { File(it) }.filter {
            it.isDirectory
        }.flatMap {
            val fileList: List<File> = it.listFiles()?.toList()?.filterNotNull() ?: emptyList()
            _pathToFileListRecursive(fileList).plus(it)
        }

private fun _pathToFileListRecursive(dataDirFiles: List<File>): List<File> =
        dataDirFiles.filter {
            it.isDirectory
        }.map {
            val fileList: List<File> = it.listFiles()?.toList()?.filterNotNull() ?: emptyList()
            _pathToFileListRecursive(fileList).plus(it)
        }.flatten()

fun createStubFromContracts(contractPaths: List<String>, host: String = "localhost", port: Int = 9000): ContractStub {
    val dataDirPaths = implicitContractDataDirs(contractPaths)
    return createStubFromContracts(contractPaths, dataDirPaths, host, port)
}

fun implicitContractDataDirs(contractPaths: List<String>) =
        contractPaths.map { implicitContractDataDir(it).absolutePath }

fun implicitContractDataDir(path: String): File {
    val contractFile = File(path)
    return File("${contractFile.absoluteFile.parent}/${contractFile.nameWithoutExtension}$DATA_DIR_SUFFIX")
}

fun stubKafkaMessage(contractPath: String, message: String, bootstrapServers: String) {
    val kafkaMessage = kafkaMessageFromJSON(getJSONObjectValue(MOCK_KAFKA_MESSAGE, jsonStringToValueMap(message)))
    ContractBehaviour(File(contractPath).readText()).assertMatchesMockKafkaMessage(kafkaMessage)
    createProducer(bootstrapServers).use {
        it.send(producerRecord(kafkaMessage))
    }
}

fun testKafkaMessage(contractPath: String, bootstrapServers: String, commit: Boolean) {
    val contractBehaviour = ContractBehaviour(File(contractPath).readText())

    val results = contractBehaviour.scenarios.map {
        testKafkaMessages(it, bootstrapServers, commit)
    }

    if(results.any { it is Result.Failure }) {
        throw ContractException(Results(results.toMutableList()).report())
    }
}

fun testKafkaMessages(scenario: Scenario, bootstrapServers: String, commit: Boolean): Result {
    return createConsumer(bootstrapServers, commit).use { consumer ->
        if(scenario.kafkaMessagePattern == null) throw ContractException("No kafka message found to test with")

        val topic = scenario.kafkaMessagePattern.topic
        consumer.subscribe(listOf(topic))

        val messages = consumer.poll(Duration.ofSeconds(1)).map {
            KafkaMessage(topic, it.key()?.let { key -> StringValue(key) }, parsedValue(it.value()))
        }

        val results = messages.map {
            scenario.kafkaMessagePattern.matches(it, scenario.resolver)
        }

        Results(results.toMutableList()).toResultIfAny()
    }
}
