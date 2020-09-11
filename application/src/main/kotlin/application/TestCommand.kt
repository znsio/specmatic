package application

import run.qontract.test.QontractJUnitSupport
import application.test.ContractExecutionListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import run.qontract.core.Constants
import run.qontract.core.utilities.*
import run.qontract.test.QontractJUnitSupport.Companion.CONTRACT_PATHS
import run.qontract.test.QontractJUnitSupport.Companion.HOST
import run.qontract.test.QontractJUnitSupport.Companion.INLINE_SUGGESTIONS
import run.qontract.test.QontractJUnitSupport.Companion.PORT
import run.qontract.test.QontractJUnitSupport.Companion.SUGGESTIONS_PATH
import run.qontract.test.QontractJUnitSupport.Companion.TIMEOUT
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.concurrent.Callable

@Command(name = "test",
        mixinStandardHelpOptions = true,
        description = ["Run contract as tests"])
class TestCommand : Callable<Unit> {
    @Autowired
    lateinit var qontractConfig: QontractConfig

    @CommandLine.Parameters(arity = "0..*", description = ["Contract file paths"])
    var contractPaths: List<String> = mutableListOf()

    @Option(names = ["--host"], description = ["The host to bind to, e.g. localhost or some locally bound IP"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["The port to bind to"])
    var port: Int = 0

    @Option(names = ["--suggestionsPath"], description = ["Location of the suggestions file"], defaultValue = "")
    lateinit var suggestionsPath: String

    @Option(names = ["--suggestions"], description = ["A json value with scenario name and multiple suggestions"], defaultValue = "")
    var suggestions: String = ""

    @Option(names = ["--https"], description = ["Use https instead of the default http"], required = false)
    var useHttps: Boolean = false

    @Option(names = ["--timeout"], description = ["Specify a timeout for the test requests"], required = false, defaultValue = "60")
    var timeout: Int = 60

    @Option(names = ["--kafkaBootstrapServers"], description = ["Kafka's Bootstrap servers"], required=false)
    var kafkaBootstrapServers: String = ""

    @Option(names = ["--kafkaHost"], description = ["The host on which to connect to Kafka"], required=false)
    var kafkaHost: String = "localhost"

    @Option(names = ["--kafkaPort"], description = ["The port on which to connect to Kafka"], required=false)
    var kafkaPort: Int = 9093

    @Option(names = ["--commit"], description = ["Commit kafka messages that have been read"], required=false)
    var commit: Boolean = false

    @Option(names = ["--workingDirectory"], description = ["The working directory in which contracts will be checked out"])
    var workingDirectory: String? = null

    @Option(names = ["--junit-report-dir"], description = ["Create junit xml reports in this directory"])
    var junitReportDirName: String? = null

    override fun call() = try {
        loadConfig()

        if(port == 0) {
            port = when {
                useHttps -> 443
                else -> 9000
            }
        }

        val protocol = when {
            port == 443 -> "https"
            useHttps -> "https"
            else -> "http"
        }

        System.setProperty(CONTRACT_PATHS, contractPaths.joinToString(","))

        System.setProperty(HOST, host)
        System.setProperty(PORT, port.toString())
        System.setProperty(TIMEOUT, timeout.toString())
        System.setProperty(SUGGESTIONS_PATH, suggestionsPath)
        System.setProperty(INLINE_SUGGESTIONS, suggestions)
        System.setProperty("protocol", protocol)

        System.setProperty("kafkaBootstrapServers", kafkaBootstrapServers)
        System.setProperty("kafkaHost", kafkaHost)
        System.setProperty("kafkaPort", kafkaPort.toString())
        System.setProperty("commit", commit.toString())

        if(kafkaPort != 0)
            System.setProperty("kafkaPort", kafkaPort.toString())

        val launcher = LauncherFactory.create()
        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(QontractJUnitSupport::class.java))
                .configurationParameter("key", "value")
                .build()
        launcher.discover(request)
        val contractExecutionListener = ContractExecutionListener()
        launcher.registerTestExecutionListeners(contractExecutionListener)

        junitReportDirName?.let { dirName ->
            val reportListener = org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener(Paths.get(dirName), PrintWriter(System.out, true))
            launcher.registerTestExecutionListeners(reportListener)
        }

        launcher.execute(request)

        contractExecutionListener.exitProcess()
    }
    catch (e: Throwable) {
        println(exceptionCauseMessage(e))
    }

    private fun loadConfig() {
        when(contractPaths.isEmpty()) {
            true -> {
                println("No contractPaths specified. Falling back to ${Constants.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY}")
                contractPaths = qontractConfig.contractTestPaths()
            }
        }
    }
}
