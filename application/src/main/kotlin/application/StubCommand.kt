package application

import picocli.CommandLine.*
import run.qontract.core.pattern.ContractException
import run.qontract.fake.ContractStub
import run.qontract.fake.implicitContractDataDir
import run.qontract.fake.createStubFromContracts
import run.qontract.fake.allDirsInTree
import run.qontract.mock.NoMatchingScenario
import java.io.File
import java.nio.file.*
import java.util.concurrent.Callable

@Command(name = "stub",
        mixinStandardHelpOptions = true,
        description = ["Start a stub server with contract"])
class StubCommand : Callable<Unit> {
    lateinit var contractFake: ContractStub

    @Parameters(arity = "1..*", description = ["Contract file paths"])
    lateinit var paths: List<String>

    @Option(names = ["--data"], description = ["Directory in which contract data may be found"], required = false)
    var dataDirs: List<String> = mutableListOf()

    @Option(names = ["--host"], description = ["Host for the http stub"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the http stub"], defaultValue = "9000")
    var port: Int = 9000

    @Option(names = ["--kafkaPort"], description = ["Port for the Kafka stub"], defaultValue = "9093")
    var kafkaPort: Int = 9093

    override fun call() = try {
        startServer()
        println("Stub server is running on http://$host:$port. Ctrl + C to stop.")
        addShutdownHook()

        val contractPathParentPaths = paths.map {
            File(it).absoluteFile.parentFile.toPath()
        }
        val contractPathDataDirPaths = paths.flatMap { contractFilePath ->
            allDirsInTree(implicitContractDataDir(contractFilePath).absolutePath).map { it.toPath() }
        }
        val dataDirPaths = dataDirs.flatMap {
            allDirsInTree(it).map { it.absoluteFile.toPath() }
        }

        val pathsToWatch = when {
            dataDirs.isNotEmpty() -> contractPathParentPaths.plus(dataDirPaths)
            else -> contractPathParentPaths.plus(contractPathDataDirPaths)
        }

        while(true) { watchForChanges(pathsToWatch) }
    } catch (e: NoMatchingScenario) {
        println(e.localizedMessage)
    } catch (e:ContractException) {
        println(e.report())
    } catch (e: Throwable) {
        println("An error occurred: ${e.localizedMessage}")
    }

    private fun watchForChanges(contractPaths: List<Path>) {
        val watchService = FileSystems.getDefault().newWatchService()

        contractPaths.forEach { contractPath ->
            contractPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE)
        }

        var key: WatchKey
        while (watchService.take().also { key = it } != null) {
            println("Detected a change...")
            key.reset()

            val (restartNeeded, changedFile) = isRestartNeeded(key)
            if(restartNeeded) {
                println("""Restarting stub server. Change in ${changedFile}""")
                restartServer()
            }
        }
    }

    private fun isRestartNeeded(key: WatchKey): Pair<Boolean, String> {
        key.pollEvents().forEach { event ->
            when {
                event.context().toString().endsWith(".json") || event.context().toString().endsWith(".qontract") -> {
                    return Pair(true, event.context().toString())
                }
                else -> {
                    println("Ingoring change ${event.kind()} to ${event.context()}")
                }
            }
        }

        return Pair(false, "")
    }

    private fun startServer() {
        contractFake = when {
            dataDirs.isNotEmpty() -> createStubFromContracts(paths, dataDirs, host, port, kafkaPort)
            else -> createStubFromContracts(paths, host, port, kafkaPort)
        }

        if(contractFake.getKafkaInstance() != null) {
            println("Started Kafka: ${contractFake.getKafkaInstance()?.bootstrapServers}")
        }
    }

    private fun restartServer() {
        println("Stopping servers...")
        try {
            stopServer()
            println("Stopped.")
        } catch (e: Throwable) { println("Error stopping server: ${e.localizedMessage}")}

        try { startServer() } catch (e: Throwable) { println("Error starting server: ${e.localizedMessage}")}
    }

    private fun stopServer() {
        contractFake.close()
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
