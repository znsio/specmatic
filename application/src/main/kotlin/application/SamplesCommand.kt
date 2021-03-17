package application

import picocli.CommandLine.*
import `in`.specmatic.core.Contract
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.stub.HttpStub
import java.io.File
import java.util.concurrent.Callable

@Command(name = "samples",
        mixinStandardHelpOptions = true,
        description = ["Generate samples of the API requests and responses for all scenarios"])
class SamplesCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Contract file path"])
    lateinit var qontractFile: File

    @Option(names = ["--host"], description = ["The host to bind to, e.g. localhost or some locally bound IP"], defaultValue = "localhost")
    var host: String = "127.0.0.1"

    @Option(names = ["--port"], description = ["The port to bind to"], defaultValue = "9000")
    var port: Int = 9000

    override fun call() {
        try {
            samples(qontractFile, host, port)
        }
        catch(e: ContractException) {
            println(e.report())
        }
        catch (exception: Throwable) {
            val indent = "  "
            val message = listOf("Exception", "Class=${exception.javaClass.name}".prependIndent(indent), exception.message?.prependIndent(indent)).joinToString("\n")
            println(message)
        }
    }
}

fun samples(gherkinFile: File, host: String, port: Int) {
    val gherkin = gherkinFile.readText().trim()

    HttpStub(gherkin, emptyList(), host, port).use { fake ->
        Contract(gherkin).samples(fake)
    }
}
