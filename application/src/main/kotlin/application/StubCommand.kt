package application

import picocli.CommandLine
import run.qontract.core.utilities.readFile
import run.qontract.fake.ContractFake
import picocli.CommandLine.*
import run.qontract.core.ContractBehaviour
import run.qontract.core.value.StringValue
import run.qontract.mock.MockScenario
import run.qontract.mock.NoMatchingScenario
import run.qontract.mock.stringToMockScenario
import java.io.File
import java.nio.file.*
import java.util.concurrent.Callable

@Command(name = "stub", version = ["0.1.0"],
        mixinStandardHelpOptions = true,
        description = ["Start a stub server with contract"])
class StubCommand : Callable<Unit> {
    lateinit var contractFake: ContractFake

    @Parameters(arity = "1..*", description = ["Contract file paths"])
    lateinit var paths: List<String>

    @Option(names = ["--host"], description = ["Host"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port"], defaultValue = "9000")
    var port: Int = 9000

    override fun call() {
        try {
            startServer()
            println("Stub server is running on http://$host:$port. Ctrl + C to stop.")
            addShutdownHook()

            val watchService = FileSystems.getDefault().newWatchService()
            val contractPaths = paths.map { Paths.get(it).toAbsolutePath() }

            contractPaths.forEach { contractPath ->
                contractPath.parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
            }

            var key: WatchKey
            while (watchService.take().also { key = it } != null) {
                key.pollEvents().forEach { event ->
                    when {
                        event.context() in contractPaths.map { it.fileName } -> {
                            println("""Restarting stub server. Change in ${event.context()}""")
                            restartServer()
                        }
                    }
                }
                key.reset()
            }
        } catch (e: NoMatchingScenario) {
            println(e.localizedMessage)
        }
    }

    private fun startServer() {
        val contractInfo = paths.map { path ->
            val contractGherkin = readFile(path)
            val contractBehaviour = ContractBehaviour(contractGherkin)
            val stubInfo = loadStubInformation(path, contractBehaviour)
            Pair(contractBehaviour, stubInfo)
        }

        contractFake = ContractFake(contractInfo, host, port)
    }

    private fun restartServer() {
        stopServer()
        startServer()
    }

    private fun stopServer() {
        contractFake.close()
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
