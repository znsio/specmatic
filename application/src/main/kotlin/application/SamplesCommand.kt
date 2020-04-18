package application

import picocli.CommandLine.*
import run.qontract.core.Contract
import run.qontract.core.pattern.ContractException
import run.qontract.core.resultReport
import run.qontract.core.utilities.readFile
import run.qontract.fake.ContractFake
import java.util.concurrent.Callable

@Command(name = "samples", version = ["0.1.0"],
        mixinStandardHelpOptions = true,
        description = ["Generate samples of the API requests and responses for all scenarios"])
class SamplesCommand : Callable<Void> {
    @Parameters(index = "0", description = ["Contract file path"])
    var path: String = ""

    @Option(names = ["--host"], description = ["Host"], defaultValue = "localhost")
    var host: String = "127.0.0.1"

    @Option(names = ["--port"], description = ["Port"], defaultValue = "9000")
    var port: Int = 9000

    override fun call(): Void? {
        try {
            val gherkin = readFile(path)

            ContractFake(gherkin, emptyList(), host, port).use { fake ->
                Contract(gherkin).test(fake)
            }
        }
        catch(e: ContractException) {
            println(e.message)
        }
        catch (exception: Throwable) {
            val indent = "  "
            val message = listOf("Exception", "Class=${exception.javaClass.name}".prependIndent(indent), exception.message?.prependIndent(indent)).joinToString("\n")
            println(message)
        }

        return null
    }
}
