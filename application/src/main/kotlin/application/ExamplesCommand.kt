package application

import `in`.specmatic.core.*
import `in`.specmatic.core.log.*
import `in`.specmatic.core.value.Value
import `in`.specmatic.mock.ScenarioStub
import picocli.CommandLine.*
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.TestExecutor
import java.io.File
import java.util.concurrent.Callable

@Command(name = "examples",
        mixinStandardHelpOptions = true,
        description = ["Generate externalised JSON example files with API requests and responses"])
class ExamplesCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Contract file path"])
    lateinit var contractFile: File

    override fun call() {
        logger = NonVerbose(CompositePrinter(listOf(JSONConsoleLogPrinter)))

        logException {
            if(!contractFile.exists())
                throw Exception("Could not find file ${contractFile.path}")

            try {
                val feature = parseContractFileToFeature(contractFile)

                feature.executeTests(object : TestExecutor {
                    override fun execute(request: HttpRequest): HttpResponse {
                        val response = feature.lookupResponse(request)

                        val stubJSON = ScenarioStub(request, response).toJSON()

                        val stubString = stubJSON.toStringLiteral()

                        File("filename").writeText(stubString)

                        return response
                    }

                    override fun setServerState(serverState: Map<String, Value>) {
                    }
                })
            } catch(e: StackOverflowError) {
                logger.log("Got a stack overflow error. You probably have a recursive data structure definition in the contract.")
            }
        }
    }
}

