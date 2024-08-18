package application

import io.specmatic.core.*
import io.specmatic.core.log.*
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.core.value.Value
import io.specmatic.mock.ScenarioStub
import picocli.CommandLine.*
import io.specmatic.test.TestExecutor
import java.io.File
import java.util.concurrent.Callable

@Command(name = "examples",
        mixinStandardHelpOptions = true,
        description = ["Generate externalised JSON example files with API requests and responses"])
class ExamplesCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Contract file path"])
    lateinit var contractFile: File

    override fun call() {
        logException {
            if(!contractFile.exists())
                throw Exception("Could not find file ${contractFile.path}")

            try {
                val feature = parseContractFileToFeature(contractFile)

                var ctr = 0

                val examplesDir = contractFile.canonicalFile.parentFile.resolve("""${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX""")
                examplesDir.mkdirs()

                feature.executeTests(object : TestExecutor {
                    override fun execute(request: HttpRequest): HttpResponse {
                        ctr += 1

                        val response = feature.lookupResponse(request).cleanup()

                        val scenarioStub = ScenarioStub(request, response)

                        val stubJSON = scenarioStub.toJSON()

                        val stubString = stubJSON.toStringLiteral()

                        val uniqueNameForApiOperation =
                            uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

                        val filename = "${uniqueNameForApiOperation}_${ctr}.json"

                        val file = examplesDir.resolve(filename)
                        val loggablePath = "Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}"

                        println(loggablePath)

                        file.writeText(stubString)

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

private fun HttpResponse.cleanup(): HttpResponse {
    return this.copy(headers = this.headers.minus(SPECMATIC_RESULT_HEADER))
}

