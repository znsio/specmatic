package application

import io.specmatic.core.Contract
import io.specmatic.core.log.CompositePrinter
import io.specmatic.core.log.JSONConsoleLogPrinter
import io.specmatic.core.log.NonVerbose
import io.specmatic.core.log.logException
import io.specmatic.core.log.logger
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.stub.HttpStub
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(name = "samples",
        mixinStandardHelpOptions = true,
        description = ["Generate samples of the API requests and responses for all scenarios"])
class SamplesCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["API specification file path"])
    lateinit var contractFile: File

    override fun call() {
        logger = NonVerbose(CompositePrinter(listOf(JSONConsoleLogPrinter)))

        logException {
            if(!contractFile.exists())
                throw Exception("Could not find file ${contractFile.path}")

            try {
                val feature = parseContractFileToFeature(contractFile)

                HttpStub(
                    feature,
                    emptyList(),
                    "127.0.0.1:56789"
                ).use { stub ->
                    feature.executeTests(stub.client)
                    Contract(feature).samples(stub)
                }
            } catch(e: StackOverflowError) {
                logger.log("Got a stack overflow error. You probably have a recursive data structure definition in your API specification.")
            }
        }
    }
}

