package application

import io.specmatic.core.examples.server.ExamplesInteractiveServer
import io.specmatic.core.log.StringLog
import io.specmatic.core.log.consoleLog
import io.specmatic.core.log.logger
import io.specmatic.core.utilities.exitWithMessage
import io.specmatic.mock.NoMatchingScenario
import picocli.CommandLine.*
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "examples",
        mixinStandardHelpOptions = true,
        description = ["Generate externalised JSON example files with API requests and responses"],
        subcommands = [ExamplesCommand.Validate::class, ExamplesCommand.Interactive::class]
)
class ExamplesCommand : Callable<Unit> {

    @Parameters(index = "0", description = ["Contract file path"], arity = "0..1")
    var contractFile: File? = null

    override fun call() {
        if(contractFile == null) {
            println("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return
        }
        if (!contractFile!!.exists())
            exitWithMessage("Could not find file ${contractFile!!.path}")

        try {
            ExamplesInteractiveServer.generate(contractFile!!)
        } catch (e: Exception) {
            exitWithMessage("An unexpected error occurred while generating examples: ${e.message}")
        }
    }

    @Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = ["Validate the examples"]
    )
    class Validate : Callable<Unit> {
        @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
        lateinit var contractFile: File

        @Option(names = ["--example-file"], description = ["Example file path"], required = true)
        lateinit var exampleFile: File

        override fun call() {
            if (!contractFile!!.exists())
                exitWithMessage("Could not find file ${contractFile!!.path}")

            try {
                ExamplesInteractiveServer.validate(contractFile, exampleFile)
                logger.log("The provided example ${exampleFile.name} is valid.")
            } catch(e: NoMatchingScenario) {
                logger.log("The provided example ${exampleFile.name} is invalid. Reason:\n")
                logger.log(e.msg.orEmpty())
                exitProcess(1)
            }
        }
    }

    @Command(
        name = "interactive",
        mixinStandardHelpOptions = true,
        description = ["Run the example generation interactively"]
    )
    class Interactive : Callable<Unit> {
        @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
        lateinit var contractFile: File

        var server: ExamplesInteractiveServer? = null

        override fun call() {
            if (!contractFile.exists())
                exitWithMessage("Could not find file ${contractFile.path}")

            server = ExamplesInteractiveServer(
                serverHost = "localhost",
                serverPort = 9001,
                contractFile = contractFile
            ).also {
                addShutdownHook()
            }
            consoleLog(StringLog("Examples Interactive server is running on http://localhost:9001/_specmatic/examples. Ctrl + C to stop."))
            while(true) sleep(10000)
        }

        private fun addShutdownHook() {
            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    try {
                        println("Shutting down examples interactive server...")
                        server?.close()
                    } catch (e: InterruptedException) {
                        currentThread().interrupt()
                    } catch (e: Throwable) {
                        logger.log(e)
                    }
                }
            })
        }
    }
}
