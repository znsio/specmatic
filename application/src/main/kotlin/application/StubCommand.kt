package application

import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine.*
import run.qontract.LogTail
import run.qontract.consoleLog
import run.qontract.core.*
import run.qontract.core.Constants.Companion.DEFAULT_HTTP_STUB_HOST
import run.qontract.core.Constants.Companion.DEFAULT_HTTP_STUB_PORT
import run.qontract.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.exitWithMessage
import run.qontract.mock.NoMatchingScenario
import run.qontract.stub.*
import java.util.concurrent.Callable

@Command(name = "stub",
        mixinStandardHelpOptions = true,
        description = ["Start a stub server with contract"])
class StubCommand : Callable<Unit> {
    var contractFake: ContractStub? = null
    var qontractKafka: QontractKafka? = null

    @Autowired
    lateinit var qontractConfig: QontractConfig

    @Parameters(arity = "0..*", description = ["Contract file paths"])
    var contractPaths: List<String> = mutableListOf()

    @Option(names = ["--data"], description = ["Directory in which contract data may be found"], required = false)
    var dataDirs: List<String> = mutableListOf()

    @Option(names = ["--host"], description = ["Host for the http stub"], defaultValue = DEFAULT_HTTP_STUB_HOST)
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the http stub"], defaultValue = DEFAULT_HTTP_STUB_PORT)
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

    @Autowired
    val watchMaker = WatchMaker()

    @Autowired
    val fileReader = RealFileReader()

    override fun call() = try {
        loadConfig()
        validateQontractFileExtensions(contractPaths, fileReader)
        startServer()
        addShutdownHook()

        val watcher = watchMaker.make(contractPaths)
        watcher.watchForChanges {
            restartServer()
        }
    } catch (e: NoMatchingScenario) {
        consoleLog(e.localizedMessage)
    } catch (e:ContractException) {
        consoleLog(e.report())
    } catch (e: Throwable) {
        consoleLog(exceptionCauseMessage(e))
    }

    private fun loadConfig() {
        when(contractPaths.isEmpty()) {
            true -> {
                println("No contractPaths specified. Falling back to $DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY")
                contractPaths = qontractConfig.contractStubPaths()
            }
        }
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

internal fun validateQontractFileExtensions(contractPaths: List<String>, fileReader: RealFileReader) {
    contractPaths.filter { fileReader.isFile(it) && fileReader.extensionIsNot(it, QONTRACT_EXTENSION) }.let {
        if (it.isNotEmpty()) {
            val files = it.joinToString("\n")
            exitWithMessage("The following files do not end with $QONTRACT_EXTENSION and cannot be used:\n$files")
        }
    }
}
