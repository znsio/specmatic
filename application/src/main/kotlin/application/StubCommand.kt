package application

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
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
import run.qontract.mock.ScenarioStub
import run.qontract.stub.*
import java.util.concurrent.Callable

data class CertInfo(val keyStoreFile: String = "", val keyStoreDir: String = "", val keyStorePassword: String = "forgotten", val keyStoreAlias: String = "qontractproxy", val keyPassword: String = "forgotten")

@Command(name = "stub",
        mixinStandardHelpOptions = true,
        description = ["Start a stub server with contract"])
class StubCommand : Callable<Unit> {
    var httpStub: ContractStub? = null
    var kafkaStub: QontractKafka? = null

    @Autowired
    private var httpStubEngine: HTTPStubEngine = HTTPStubEngine()

    @Autowired
    private var kafkaStubEngine: KafkaStubEngine = KafkaStubEngine()

    @Autowired
    private var stubLoaderEngine: StubLoaderEngine = StubLoaderEngine()

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    var qontractConfig: QontractConfig = QontractConfig()

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
    lateinit var kafkaHost: String

    @Option(names = ["--kafkaPort"], description = ["Port for the Kafka stub"], defaultValue = "9093", required = false)
    lateinit var kafkaPort: String

    @Option(names = ["--strict"], description = ["Start HTTP stub in strict mode"], required = false)
    var strictMode: Boolean = false

    @Option(names = ["--passThroughTargetBase"], description = ["All requests that did not match a url in any contract will be forwarded to this service"])
    var passThroughTargetBase: String = ""

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
    val fileOperations = FileOperations()

    @Autowired
    val httpClientFactory = HttpClientFactory()

    override fun call() {
        try {
            contractPaths = loadConfig()
            validateQontractFileExtensions(contractPaths, fileOperations)
            startServer()

            if(httpStub != null || kafkaStub != null) {
                addShutdownHook()

                val watcher = watchMaker.make(contractPaths)
                watcher.watchForChanges {
                    restartServer()
                }
            }
        } catch (e: NoMatchingScenario) {
            consoleLog(e.localizedMessage)
        } catch (e:ContractException) {
            consoleLog(e.report())
        } catch (e: Throwable) {
            consoleLog(exceptionCauseMessage(e))
        }
    }

    private fun loadConfig() = contractPaths.ifEmpty {
        println("No contractPaths specified. Falling back to $DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY")
        qontractConfig.contractStubPaths()
    }

    private fun startServer() {
        val stubData = stubLoaderEngine.loadStubs(contractPaths, dataDirs)

        val certInfo = CertInfo(keyStoreFile, keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)

        httpStub = httpStubEngine.runHTTPStub(stubData, host, port, certInfo, strictMode, passThroughTargetBase, httpClientFactory)
        kafkaStub = kafkaStubEngine.runKafkaStub(stubData, kafkaHost, kafkaPort.toInt(), startKafka)

        LogTail.storeSnapshot()
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
        httpStub?.close()
        httpStub = null

        kafkaStub?.close()
        kafkaStub = null
    }

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                try {
                    consoleLog("Shutting down stub servers")
                    httpStub?.close()
                    kafkaStub?.close()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        })
    }
}

internal fun validateQontractFileExtensions(contractPaths: List<String>, fileOperations: FileOperations) {
    contractPaths.filter { fileOperations.isFile(it) && fileOperations.extensionIsNot(it, QONTRACT_EXTENSION) }.let {
        if (it.isNotEmpty()) {
            val files = it.joinToString("\n")
            exitWithMessage("The following files do not end with $QONTRACT_EXTENSION and cannot be used:\n$files")
        }
    }
}

@Component
class StubLoaderEngine {
    fun loadStubs(contractPaths: List<String>, dataDirs: List<String>): List<Pair<Feature, List<ScenarioStub>>> {
        return when {
            dataDirs.isNotEmpty() -> loadContractStubsFromFiles(contractPaths, dataDirs)
            else -> loadContractStubsFromImplicitPaths(contractPaths)
        }
    }
}

@Component
class KafkaStubEngine {
    fun runKafkaStub(stubs: List<Pair<Feature, List<ScenarioStub>>>, kafkaHost: String, kafkaPort: Int, startKafka: Boolean): QontractKafka? {
        val features = stubs.map { it.first }

        val kafkaExpectations = contractInfoToKafkaExpectations(stubs)
        val validationResults = kafkaExpectations.map { stubData ->
            features.asSequence().map {
                it.matchesMockKafkaMessage(stubData.kafkaMessage)
            }.find {
                it is Result.Failure
            } ?: Result.Success()
        }

        return when {
            validationResults.any { it is Result.Failure } -> {
                val results = Results(validationResults.toMutableList())
                consoleLog("Can't load Kafka mocks:\n${results.report()}")
                null
            }
            hasKafkaScenarios(features) -> {
                val qontractKafka = when {
                    startKafka -> {
                        println("Starting local Kafka server...")
                        QontractKafka(kafkaPort).also {
                            consoleLog("Started local Kafka server: ${it.bootstrapServers}")
                        }
                    }
                    else -> null
                }

                stubKafkaContracts(kafkaExpectations, qontractKafka?.bootstrapServers
                        ?: "PLAINTEXT://$kafkaHost:$kafkaPort", ::createTopics, ::createProducer)

                qontractKafka
            }
            else -> null
        }
    }
}

@Component
class HTTPStubEngine {
    fun runHTTPStub(stubs: List<Pair<Feature, List<ScenarioStub>>>, host: String, port: Int, certInfo: CertInfo, strictMode: Boolean, passThroughTargetBase: String = "", httpClientFactory: HttpClientFactory): HttpStub? {
        val features = stubs.map { it.first }

        return when {
            hasHttpScenarios(features) -> {
                val httpExpectations = contractInfoToHttpExpectations(stubs)

                val httpFeatures = features.map {
                    val httpScenarios = it.scenarios.filter { scenario ->
                        scenario.kafkaMessagePattern == null
                    }

                    it.copy(scenarios = httpScenarios)
                }

                val keyStoreData = getHttpsCert(certInfo.keyStoreFile, certInfo.keyStoreDir, certInfo.keyStorePassword, certInfo.keyStoreAlias, certInfo.keyPassword)
                HttpStub(httpFeatures, httpExpectations, host, port, ::consoleLog, strictMode, keyStoreData, passThroughTargetBase = passThroughTargetBase, httpClientFactory = httpClientFactory).also {
                    val protocol = if (keyStoreData != null) "https" else "http"
                    consoleLog("Stub server is running on ${protocol}://$host:$port. Ctrl + C to stop.")
                }
            }
            else -> null
        }
    }
}

internal fun hasHttpScenarios(behaviours: List<Feature>): Boolean {
    return behaviours.any {
        it.scenarios.any { scenario ->
            scenario.kafkaMessagePattern == null
        }
    }
}

internal fun hasKafkaScenarios(behaviours: List<Feature>): Boolean {
    return behaviours.any {
        it.scenarios.any { scenario ->
            scenario.kafkaMessagePattern != null
        }
    }
}
