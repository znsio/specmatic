package application

import run.qontract.core.utilities.readFile
import run.qontract.fake.ContractFake
import picocli.CommandLine.*
import run.qontract.core.ContractBehaviour
import run.qontract.core.value.StringValue
import run.qontract.mock.MockScenario
import run.qontract.mock.NoMatchingScenario
import run.qontract.mock.stringToMockScenario
import java.io.File
import java.util.concurrent.Callable

@Command(name = "stub", version = ["0.1.0"],
        mixinStandardHelpOptions = true,
        description = ["Start a stub server with contract"])
class StubCommand : Callable<Void> {
    lateinit var contractFake: ContractFake

    @Parameters(index = "0", description = ["Contract file path"])
    lateinit var path: String

    @Option(names = ["--host"], description = ["Host"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port"], defaultValue = "9000")
    var port: Int = 9000

    override fun call(): Void? {
        try {
            val contractGherkin = readFile(path)
            val contractBehaviour = ContractBehaviour(contractGherkin)
            val stubInfo = loadStubInformation(path, contractBehaviour)

            addShutdownHook()
            contractFake = ContractFake(contractGherkin, stubInfo, host, port)

            println("Stub server is running on http://$host:$port. Ctrl + C to stop.")
            while (true) {
                Thread.sleep(1000)
            }
        } catch(e: NoMatchingScenario) {
            println(e.localizedMessage)
        }

        return null
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

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                try {
                    println("Shutting down stub server")
                    contractFake.close()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        })
    }
}
