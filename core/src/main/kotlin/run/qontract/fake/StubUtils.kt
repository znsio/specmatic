@file:JvmName("StubUtils")
package run.qontract.fake

import run.qontract.core.ContractBehaviour
import run.qontract.core.utilities.readFile
import run.qontract.core.value.StringValue
import run.qontract.mock.MockScenario
import run.qontract.mock.stringToMockScenario
import java.io.File

fun createStubFromPaths(paths: List<String>, host: String, port: Int): ContractStub {
    val contractInfo = paths.map { path ->
        val contractGherkin = readFile(path)
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val stubInfo = loadStubInformation(path, contractBehaviour)
        Pair(contractBehaviour, stubInfo)
    }

    return ContractFake(contractInfo, host, port)
}

private fun loadStubInformation(filePath: String, contractBehaviour: ContractBehaviour): List<MockScenario> =
        stubDataFiles(filePath).map { file ->
            println("Loading data from ${file.name}")

            stringToMockScenario(StringValue(file.readText(Charsets.UTF_8)))
                    .also {
                        contractBehaviour.matchingMockResponse(it)
                    }
        }

private fun stubDataFiles(path: String): List<File> {
    val contractFile = File(path)
    val stubDataDir = File("${contractFile.absoluteFile.parent}/${contractFile.nameWithoutExtension}_data")
    println("Loading data files from ${stubDataDir.absolutePath} ")

    return stubDataDir.listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()
}
