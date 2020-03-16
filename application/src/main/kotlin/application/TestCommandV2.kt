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

@Command(name = "test-v2", version = ["0.1.0"],
        mixinStandardHelpOptions = true,
        description = ["Run contract as junit tests"])
class TestCommandV2 : Callable<Void> {

    @Option(names = ["--path"], description = ["Contract location"], required = true)
    lateinit var path: String

    @Option(names = ["--host"], description = ["Host"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port"], defaultValue = "9000")
    lateinit var port: Integer

    @Option(names = ["--suggestions"], description = ["run.qontract.core.Suggestions location"], defaultValue = "")
    lateinit var suggestionsPath: String

    @Command
    fun run() {
        System.setProperty("path", path)
        System.setProperty("host", host)
        System.setProperty("port", port.toString())
        System.setProperty("suggestions", suggestionsPath)
        val launcher = LauncherFactory.create()
        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(QontractJUnitSupport::class.java))
                .build()
        launcher.discover(request)
        val contractExecutionListener = ContractExecutionListener()
        launcher.registerTestExecutionListeners(contractExecutionListener)
        launcher.execute(request)
        contractExecutionListener.exitProcess()
    }

    override fun call(): Void? {
        CommandLine(TestCommandV2()).usage(System.out)
        return null
    }

}
