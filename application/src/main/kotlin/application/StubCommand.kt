package application

import picocli.CommandLine.*
import run.qontract.LogTail
import run.qontract.consoleLog
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.mock.NoMatchingScenario
import run.qontract.stub.*
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.util.concurrent.Callable

@Command(name = "stub",
        mixinStandardHelpOptions = true,
        description = ["Start a stub server with contract"])
class StubCommand : Callable<Unit> {
    var contractFake: ContractStub? = null
    var qontractKafka: QontractKafka? = null

    @Parameters(arity = "1..*", description = ["Contract file paths"])
    lateinit var contractPaths: List<String>

    @Option(names = ["--data"], description = ["Directory in which contract data may be found"], required = false)
    var dataDirs: List<String> = mutableListOf()

    @Option(names = ["--host"], description = ["Host for the http stub"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the http stub"], defaultValue = "9000")
    var port: Int = 0

    @Option(names = ["--startKafka"], description = ["Host on which to dump the stubbed kafka message"], defaultValue = "false")
    var startKafka: Boolean = false

    @Option(names = ["--kafkaHost"], description = ["Host on which to dump the stubbed kafka message"], defaultValue = "localhost", required = false)
    var kafkaHost: String = "127.0.0.1"

    @Option(names = ["--kafkaPort"], description = ["Port for the Kafka stub"], defaultValue = "9093", required = false)
    var kafkaPort: Int = 9093

    @Option(names = ["--strict"], description = ["Start HTTP stub in strict mode"], required = false)
    var strictMode: Boolean = false

    @Option(names = ["--httpsKeyStore"], description = ["EXPERIMENTAL: Run the proxy on https using a key in this store"])
    var keyStoreFile = ""

    @Option(names = ["--httpsKeyStoreDir"], description = ["EXPERIMENTAL: Run the proxy on https, create a store named qontract.jks in this directory"])
    var keyStoreDir = ""

    @Option(names = ["--httpsKeyStorePassword"], description = ["EXPERIMENTAL: Run the proxy on https, password for pre-existing key store"])
    var keyStorePassword = "forgotten"

    @Option(names = ["--httpsKeyAlias"], description = ["EXPERIMENTAL: Run the proxy on https using a key by this name"])
    var keyStoreAlias = "qontractproxy"

    @Option(names = ["--httpsPassword"], description = ["EXPERIMENTAL: Key password if any"])
    var keyPassword = "forgotten"

    override fun call() = try {
        startServer()
        addShutdownHook()

        val contractPathParentPaths = contractPaths.map {
            File(it).absoluteFile.parentFile.toPath()
        }
        val contractPathDataDirPaths = contractPaths.flatMap { contractFilePath ->
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
        consoleLog(e.localizedMessage)
    } catch (e:ContractException) {
        consoleLog(e.report())
    } catch (e: Throwable) {
        consoleLog(exceptionCauseMessage(e))
    }

    private fun watchForChanges(contractPaths: List<Path>) {
        val watchService = FileSystems.getDefault().newWatchService()

        contractPaths.forEach { contractPath ->
            contractPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE)
        }

        var key: WatchKey
        while (watchService.take().also { key = it } != null) {
            key.reset()

            val (restartNeeded, changedFile) = isRestartNeeded(key)
            if(restartNeeded) {
                consoleLog("""Restarting stub server. Change in ${changedFile}""")
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
            }
        }

        return Pair(false, "")
    }

    private fun startServer() {
        val stubs = when {
            dataDirs.isNotEmpty() -> loadContractStubsFromFiles(contractPaths, dataDirs)
            else -> loadContractStubsFromImplicitPaths(contractPaths)
        }

        val behaviours = stubs.map { it.first }

        contractFake = when {
            hasHttpScenarios(behaviours) -> {
                val httpExpectations = contractInfoToHttpExpectations(stubs)
                val httpBehaviours = behaviours.map {
                    it.copy(scenarios = it.scenarios.filter { scenario ->
                        scenario.kafkaMessagePattern == null
                    })
                }

                val keyStoreData = getHttpsCert(keyStoreFile, keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)
                HttpStub(httpBehaviours, httpExpectations, host, port, ::consoleLog, strictMode, keyStoreData).also {
                    val protocol = if(keyStoreData != null) "https" else "http"
                    consoleLog("Stub server is running on ${protocol}://$host:$port. Ctrl + C to stop.")
                }
            }
            else -> null
        }

        val kafkaExpectations = contractInfoToKafkaExpectations(stubs)
        val validationResults = kafkaExpectations.map { stubData ->
            behaviours.asSequence().map { it.matchesMockKafkaMessage(stubData.kafkaMessage) }.find { it is Result.Failure } ?: Result.Success()
        }

        if(validationResults.any { it is Result.Failure }) {
            val results = Results(validationResults.toMutableList())
            consoleLog("Can't load Kafka mocks:\n${results.report()}")
        }
        else if(hasKafkaScenarios(behaviours)) {
            if(startKafka) {
                qontractKafka = QontractKafka(kafkaPort)
                consoleLog("Started local Kafka server: ${qontractKafka?.bootstrapServers}")
            }

            stubKafkaContracts(kafkaExpectations, qontractKafka?.bootstrapServers ?: "PLAINTEXT://$kafkaHost:$kafkaPort", ::createTopics, ::createProducer)
        }

        LogTail.storeSnapshot()
    }

    private fun hasKafkaScenarios(behaviours: List<Feature>): Boolean {
        return behaviours.any {
            it.scenarios.any { scenario ->
                scenario.kafkaMessagePattern != null
            }
        }
    }

    private fun hasHttpScenarios(behaviours: List<Feature>): Boolean {
        return behaviours.any {
            it.scenarios.any { scenario ->
                scenario.kafkaMessagePattern == null
            }
        }
    }

    private fun restartServer() {
        consoleLog("Stopping servers...")
        try {
            stopServer()
            consoleLog("Stopped.")
        } catch (e: Throwable) {
            consoleLog("Error stopping server: ${e.localizedMessage}")
        }

        try { startServer() } catch (e: Throwable) {
            consoleLog("Error starting server: ${e.localizedMessage}")
        }
    }

    private fun stopServer() {
        contractFake?.close()
        contractFake = null

        qontractKafka?.close()
        qontractKafka = null
    }

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                try {
                    consoleLog("Shutting down stub servers")
                    contractFake?.close()
                    qontractKafka?.close()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        })
    }
}
