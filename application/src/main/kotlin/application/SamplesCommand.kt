package application

import `in`.specmatic.core.*
import `in`.specmatic.core.log.JSONConsoleLogPrinter
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.log.logException
import picocli.CommandLine.*
import `in`.specmatic.stub.HttpStub
import java.io.File
import java.util.concurrent.Callable

@Command(name = "samples",
        mixinStandardHelpOptions = true,
        description = ["Generate samples of the API requests and responses for all scenarios"])
class SamplesCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Contract file path"])
    lateinit var contractFile: File

    override fun call() {
        logger.printer.printers.apply {
            clear()
            add(JSONConsoleLogPrinter)
        }

        logException {
            if(!contractFile.exists())
                throw Exception("Could not find file ${contractFile.path}")

            try {
                val feature = parseContractFileToFeature(contractFile)

                HttpStub(feature, emptyList(), "127.0.0.1", 56789).use { fake ->
                    Contract(feature).samples(fake)
                }
            } catch(e: StackOverflowError) {
                logger.log("Got a stack overflow error. You probably have a recursive data structure definition in the contract.")
            }
        }
    }
}

