@file:JvmName("StubUtils")
package run.qontract.fake

import run.qontract.consoleLog
import run.qontract.core.CONTRACT_EXTENSION
import run.qontract.core.ContractBehaviour
import run.qontract.core.DATA_DIR_SUFFIX
import run.qontract.core.utilities.readFile
import run.qontract.core.value.StringValue
import run.qontract.mock.MockScenario
import run.qontract.mock.NoMatchingScenario
import run.qontract.mock.stringToMockScenario
import java.io.File

fun createStubFromContractAndData(contractGherkin: String, dataDirectory: String, host: String = "localhost", port: Int = 900): ContractStub {
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

fun createStubFromContracts(contractPaths: List<String>, dataDirPaths: List<String>, host: String = "localhost", port: Int = 9000): ContractStub {
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
