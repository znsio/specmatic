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
import run.qontract.core.Contract
import run.qontract.core.utilities.readFile
import run.qontract.fake.ContractFake
import java.util.concurrent.Callable

@Command(name = "samples", version = ["0.1.0"],
        mixinStandardHelpOptions = true,
        description = ["Run contract as tests"])
class SamplesCommand : Callable<Void> {

    @Option(names = ["--path"], description = ["Contract location"], required = true)
    var path: String = ""

    @Option(names = ["--host"], description = ["Host"], defaultValue = "localhost")
    var host: String = "127.0.0.1"

    @Option(names = ["--port"], description = ["Port"], defaultValue = "9000")
    var port: Int = 9000

    override fun call(): Void? {
        try {
            val gherkin = readFile(path)
            ContractFake(gherkin, host, port).use { fake ->
                Contract(gherkin).test(fake)
            }
        } catch (exception: Throwable) {
            println("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
        }

        return null
    }
}
