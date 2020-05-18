package application

import run.qontract.test.QontractJUnitSupport
import run.qontract.test.ContractExecutionListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@Command(name = "test",
        mixinStandardHelpOptions = true,
        description = ["Run contract as tests"])
class TestCommand : Callable<Void> {
    @CommandLine.Parameters(index = "0", description = ["Contract file path"])
    lateinit var path: String

    @Option(names = ["--host"], description = ["The host to bind to, e.g. localhost or some locally bound IP"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["The port to bind to"])
    var port: Int = 0

    @Option(names = ["--suggestions"], description = ["Location of the suggestions file"], defaultValue = "")
    lateinit var suggestionsPath: String

    @Option(names = ["--https"], description = ["Use https instead of the default http"], required = false)
    var useHttps: Boolean = false

    @Option(names = ["--checkBackwardCompatibility", "--check", "-c"], description = ["Identify versions of the contract prior to the one specified, and verify backward compatibility from the earliest to the latest"])
    var checkBackwardCompatibility: Boolean = false

    @Option(names = ["--kafkaBootstrapServers"], description = ["Kafka's Bootstrap servers"], required=false)
    var kafkaBootstrapServers: String = ""

    @Option(names = ["--kafkaHost"], description = ["The host on which to connect to Kafka"], required=false)
    var kafkaHost: String = "localhost"

    @Option(names = ["--kafkaPort"], description = ["The port on which to connect to Kafka"], required=false)
    var kafkaPort: Int = 9093

    @Option(names = ["--commit"], description = ["Commit kafka messages that have been read"], required=false)
    var commit: Boolean = false

    override fun call(): Void? {
        try {
            if(port == 0) {
                port = when {
                    useHttps -> 443
                    else -> 9000
                }
            }

            val protocol = if(useHttps) "https" else "http"

            System.setProperty("path", path)
            System.setProperty("host", host)
            System.setProperty("port", port.toString())
            System.setProperty("suggestions", suggestionsPath)
            System.setProperty("checkBackwardCompatibility", checkBackwardCompatibility.toString())
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
                    .build()
            launcher.discover(request)
            val contractExecutionListener = ContractExecutionListener()
            launcher.registerTestExecutionListeners(contractExecutionListener)
            launcher.execute(request)
            contractExecutionListener.exitProcess()
        } catch (exception: Throwable) {
            println("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
        }
        return null
    }
}
