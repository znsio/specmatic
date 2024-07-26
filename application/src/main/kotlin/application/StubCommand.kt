package application

import io.specmatic.core.*
import io.specmatic.core.Configuration.Companion.DEFAULT_HTTP_STUB_HOST
import io.specmatic.core.Configuration.Companion.DEFAULT_HTTP_STUB_PORT
import io.specmatic.core.log.*
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.core.utilities.exitIfAnyDoNotExist
import io.specmatic.core.utilities.exitWithMessage
import io.specmatic.stub.ContractStub
import io.specmatic.stub.HttpClientFactory
import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable


@Command(name = "stub",
        mixinStandardHelpOptions = true,
        description = ["Start a stub server with contract"])
class StubCommand : Callable<Unit> {
    var httpStub: ContractStub? = null

    @Autowired
    private var httpStubEngine: HTTPStubEngine = HTTPStubEngine()

    @Autowired
    private var stubLoaderEngine: StubLoaderEngine = StubLoaderEngine()

    @Autowired
    private var specmaticConfig: SpecmaticConfig = SpecmaticConfig()

    @Parameters(arity = "0..*", description = ["Contract file paths"])
    var contractPaths: List<String> = mutableListOf()

    @Option(names = ["--data", "--examples"], description = ["Directories containing JSON examples"], required = false)
    var exampleDirs: List<String> = mutableListOf()

    @Option(names = ["--host"], description = ["Host for the http stub"], defaultValue = DEFAULT_HTTP_STUB_HOST)
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the http stub"], defaultValue = DEFAULT_HTTP_STUB_PORT)
    var port: Int = 0

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

    @Option(names = ["--jsonLog"], description = ["Directory in which to write a JSON log"])
    var jsonLog: String? = null

    @Option(names = ["--jsonConsoleLog"], description = ["Console log should be in JSON format"])
    var jsonConsoleLog: Boolean = false

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

    private var contractSources:List<ContractPathData> = emptyList()

    var specmaticConfigPath: String? = null

    override fun call() {
        val logPrinters = configureLogPrinters()

        logger = if(verbose)
            Verbose(CompositePrinter(logPrinters))
        else
            NonVerbose(CompositePrinter(logPrinters))

        if (configFileName != null) {
            Configuration.globalConfigFileName = configFileName as String
        } else {
            Configuration.globalConfigFileName = getConfigFileName()
        }

        try {
            contractSources = when (contractPaths.isEmpty()) {
                true -> {
                    logger.debug("No contractPaths specified. Using configuration file named $configFileName")
                    specmaticConfigPath = File(Configuration.globalConfigFileName).canonicalPath
                    specmaticConfig.contractStubPathData()
                }
                else -> contractPaths.map {
                    ContractPathData("", it)
                }
            }
            contractPaths = contractSources.map { it.path }
            exitIfAnyDoNotExist("The following specifications do not exist", contractPaths)
            validateContractFileExtensions(contractPaths, fileOperations)
            startServer()

            if(httpStub != null) {
                addShutdownHook()
                val watcher = watchMaker.make(contractPaths.plus(exampleDirs))
                watcher.watchForChanges {
                    restartServer()
                }
            }
        } catch (e: Throwable) {
            consoleLog(e)
        }
    }

    private fun configureLogPrinters(): List<LogPrinter> {
        val consoleLogPrinter = configureConsoleLogPrinter()
        val textLogPrinter = configureTextLogPrinter()
        val jsonLogPrinter = configureJSONLogPrinter()

        return consoleLogPrinter.plus(textLogPrinter).plus(jsonLogPrinter)
    }

    private fun configureConsoleLogPrinter(): List<LogPrinter> {
        if (noConsoleLog)
            return emptyList()

        if (jsonConsoleLog)
            return listOf(JSONConsoleLogPrinter)

        return listOf(ConsolePrinter)
    }

    private fun configureJSONLogPrinter(): List<LogPrinter> = jsonLog?.let {
        listOf(JSONFilePrinter(LogDirectory(it, logPrefix, "json", "log")))
    } ?: emptyList()

    private fun configureTextLogPrinter(): List<LogPrinter> = textLog?.let {
        listOf(TextFilePrinter(LogDirectory(it, logPrefix, "", "log")))
    } ?: emptyList()


    private fun startServer() {
        val workingDirectory = WorkingDirectory()
        val stubData = stubLoaderEngine.loadStubs(contractSources, exampleDirs)

        val certInfo = CertInfo(keyStoreFile, keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)

        port = when (isDefaultPort(port)) {
            true -> if (portIsInUse(host, port)) findRandomFreePort() else port
            false -> port
        }
        httpStub = httpStubEngine.runHTTPStub(stubData, host, port, certInfo, strictMode, passThroughTargetBase, specmaticConfigPath, httpClientFactory, workingDirectory)

        LogTail.storeSnapshot()
    }

    private fun isDefaultPort(port:Int): Boolean {
        return DEFAULT_HTTP_STUB_PORT == port.toString()
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
    }

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                try {
                    consoleLog(StringLog("Shutting down stub servers"))
                    httpStub?.close()
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
            exitWithMessage("The following files do not end with $CONTRACT_EXTENSIONS and cannot be used:\n$files")
        }
    }
}
