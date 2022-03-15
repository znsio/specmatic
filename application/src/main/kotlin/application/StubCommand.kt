package application

import `in`.specmatic.core.*
import `in`.specmatic.core.Configuration.Companion.DEFAULT_HTTP_STUB_HOST
import `in`.specmatic.core.Configuration.Companion.DEFAULT_HTTP_STUB_PORT
import `in`.specmatic.core.log.*
import `in`.specmatic.core.utilities.exitWithMessage
import `in`.specmatic.stub.ContractStub
import `in`.specmatic.stub.HttpClientFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import picocli.CommandLine.*
import java.util.concurrent.Callable

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
    private var specmaticConfig: SpecmaticConfig = SpecmaticConfig()

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

    @Option(names = ["--httpsKeyStore"], description = ["Run the proxy on https using a key in this store"])
    var keyStoreFile = ""

    @Option(names = ["--httpsKeyStoreDir"], description = ["Run the proxy on https, create a store named $APPLICATION_NAME_LOWER_CASE.jks in this directory"])
    var keyStoreDir = ""

    @Option(names = ["--httpsKeyStorePassword"], description = ["Run the proxy on https, password for pre-existing key store"])
    var keyStorePassword = "forgotten"

    @Option(names = ["--httpsKeyAlias"], description = ["Run the proxy on https using a key by this name"])
    var keyStoreAlias = "${APPLICATION_NAME_LOWER_CASE}proxy"

    @Option(names = ["--httpsPassword"], description = ["Key password if any"])
    var keyPassword = "forgotten"

    @Option(names = ["--debug"], description = ["Debug logs"])
    var verbose = false

    @Option(names = ["--config"], description = ["Configuration file name ($APPLICATION_NAME_LOWER_CASE.json by default)"])
    var configFileName: String? = null

    @Option(names = ["--textLog"], description = ["Directory in which to write a text log"])
    var textLog: String? = null

    @Option(names = ["--jsonLog"], description = ["Directory in which to write a json log"])
    var jsonLog: String? = null

    @Option(names = ["--noConsoleLog"], description = ["Don't log to console"])
    var noConsoleLog: Boolean = false

    @Option(names = ["--logPrefix"], description = ["Prefix of log file"])
    var logPrefix: String = "specmatic"

    @Autowired
    val watchMaker = WatchMaker()

    @Autowired
    val fileOperations = FileOperations()

    @Autowired
    val httpClientFactory = HttpClientFactory()

    override fun call() {
        val logPrinters = mutableListOf<LogPrinter>()

        if(!noConsoleLog) {
            logPrinters.add(ConsolePrinter)
        }

        textLog?.let {
            logPrinters.add(TextFilePrinter(LogDirectory(it, logPrefix, "", "log")))
            logger.printer.printers.add(TextFilePrinter(LogDirectory(it, logPrefix, "", "log")))
        }

        jsonLog?.let {
            logPrinters.add(JSONFilePrinter(LogDirectory(it, logPrefix, "json", "log")))
            logger.printer.printers.add(JSONFilePrinter(LogDirectory(it, logPrefix, "json", "log")))
        }

        logger = if(verbose)
            Verbose(CompositePrinter(logPrinters))
        else
            NonVerbose(CompositePrinter(logPrinters))

        configFileName?.let {
            Configuration.globalConfigFileName = it
        }

        try {
            contractPaths = loadConfig()
            validateContractFileExtensions(contractPaths, fileOperations)
            startServer()

            if(httpStub != null || kafkaStub != null) {
                addShutdownHook()

                val watcher = watchMaker.make(contractPaths.plus(dataDirs))
                watcher.watchForChanges {
                    restartServer()
                }
            }
        } catch (e: Throwable) {
            consoleLog(e)
        }
    }

    private fun loadConfig() = contractPaths.ifEmpty {
        logger.debug("No contractPaths specified. Using configuration file named $configFileName")
        specmaticConfig.contractStubPaths()
    }

    private fun startServer() {
        val workingDirectory = WorkingDirectory()
        val stubData = stubLoaderEngine.loadStubs(contractPaths, dataDirs)

        val certInfo = CertInfo(keyStoreFile, keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)

        httpStub = httpStubEngine.runHTTPStub(stubData, host, port, certInfo, strictMode, passThroughTargetBase, httpClientFactory, workingDirectory)
        kafkaStub = kafkaStubEngine.runKafkaStub(stubData, kafkaHost, kafkaPort.toInt(), startKafka)

        LogTail.storeSnapshot()
    }

    private fun restartServer() {
        consoleLog(StringLog("Stopping servers..."))
        try {
            stopServer()
            consoleLog(StringLog("Stopped."))
        } catch (e: Throwable) {
            consoleLog(e,"Error stopping server")
        }

        try { startServer() } catch (e: Throwable) {
            consoleLog(e, "Error starting server")
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
                    consoleLog(StringLog("Shutting down stub servers"))
                    httpStub?.close()
                    kafkaStub?.close()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        })
    }
}

internal fun validateContractFileExtensions(contractPaths: List<String>, fileOperations: FileOperations) {
    contractPaths.filter { fileOperations.isFile(it) && fileOperations.extensionIsNot(it, CONTRACT_EXTENSIONS) }.let {
        if (it.isNotEmpty()) {
            val files = it.joinToString("\n")
            exitWithMessage("The following files do not end with ${CONTRACT_EXTENSIONS} and cannot be used:\n$files")
        }
    }
}
