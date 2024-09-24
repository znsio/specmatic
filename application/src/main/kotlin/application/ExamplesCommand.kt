package application

import io.specmatic.core.Result
import io.specmatic.core.Results
import io.specmatic.core.examples.server.ExamplesInteractiveServer
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.validate
import io.specmatic.core.examples.server.loadExternalExamples
import io.specmatic.core.log.*
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.Flags
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.utilities.exitWithMessage
import io.specmatic.mock.ScenarioStub
import picocli.CommandLine.*
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "examples",
    mixinStandardHelpOptions = true,
    description = ["Generate externalised JSON example files with API requests and responses"],
    subcommands = [ExamplesCommand.Validate::class, ExamplesCommand.Interactive::class]
)
class ExamplesCommand : Callable<Unit> {
    @Option(
        names = ["--filter-name"],
        description = ["Use only APIs with this value in their name"],
        defaultValue = "\${env:SPECMATIC_FILTER_NAME}"
    )
    var filterName: String = ""

    @Option(
        names = ["--filter-not-name"],
        description = ["Use only APIs which do not have this value in their name"],
        defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}"
    )
    var filterNotName: String = ""

    @Option(
        names = ["--extensive"],
        description = ["Generate all examples (by default, generates one example per 2xx API)"],
        defaultValue = "false"
    )
    var extensive: Boolean = false

    @Parameters(index = "0", description = ["Contract file path"], arity = "0..1")
    var contractFile: File? = null

    @Option(names = ["--debug"], description = ["Debug logs"])
    var verbose = false

    override fun call() {
        if (contractFile == null) {
            println("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return
        }
        if (!contractFile!!.exists())
            exitWithMessage("Could not find file ${contractFile!!.path}")

        configureLogger(this.verbose)

        try {
            ExamplesInteractiveServer.generate(
                contractFile!!,
                ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName),
                extensive
            )
        } catch (e: Throwable) {
            logger.log(e)
            exitProcess(1)
        }
    }


    @Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = ["Validate the examples"]
    )
    class Validate : Callable<Int> {
        @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
        lateinit var contractFile: File

        @Option(names = ["--example-file"], description = ["Example file path"], required = false)
        val exampleFile: File? = null

        @Option(names = ["--debug"], description = ["Debug logs"])
        var verbose = false

        @Option(
            names = ["--filter-name"],
            description = ["Validate examples of only APIs with this value in their name"],
            defaultValue = "\${env:SPECMATIC_FILTER_NAME}"
        )
        var filterName: String = ""

        @Option(
            names = ["--filter-not-name"],
            description = ["Validate examples of only APIs which do not have this value in their name"],
            defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}"
        )
        var filterNotName: String = ""

        override fun call(): Int {
            if (!contractFile.exists())
                exitWithMessage("Could not find file ${contractFile.path}")

            configureLogger(this.verbose)

            if (exampleFile != null) {
                try {
                    Result.fromResults(validate(contractFile, exampleFile).values.filterIsInstance<Result.Failure>()).throwOnFailure()

                    logger.log("The provided example ${exampleFile.name} is valid.")
                } catch (e: ContractException) {
                    logger.log("The provided example ${exampleFile.name} is invalid. Reason:\n")
                    logger.log(exceptionCauseMessage(e))
                    return 1
                }
            } else {
                val scenarioFilter = ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName)

                val (validateInline, validateExternal) = if(!Flags.getBooleanValue("VALIDATE_INLINE_EXAMPLES") && !Flags.getBooleanValue("IGNORE_INLINE_EXAMPLES")) {
                    true to true
                } else {
                    Flags.getBooleanValue("VALIDATE_INLINE_EXAMPLES") to Flags.getBooleanValue("IGNORE_INLINE_EXAMPLES")
                }

                val feature = parseContractFileToFeature(contractFile)

                val inlineExampleValidationResults = if(validateInline) {
                    val inlineExamples = feature.stubsFromExamples.mapValues {
                        it.value.map {
                            ScenarioStub(it.first, it.second)
                        }
                    }

                    ExamplesInteractiveServer.validate(feature, examples = inlineExamples, inline = true, scenarioFilter = scenarioFilter)
                } else emptyMap()

                val externalExampleValidationResults = if(validateExternal) {
                    val (externalExampleDir, externalExamples) = loadExternalExamples(contractFile)

                    if(!externalExampleDir.exists()) {
                        logger.log("$externalExampleDir does not exist, did not find any files to validate")
                        return 1
                    }

                    if(externalExamples.none()) {
                        logger.log("No example files found in $externalExampleDir")
                        return 1
                    }

                    ExamplesInteractiveServer.validate(feature, examples = externalExamples, scenarioFilter = scenarioFilter)
                } else emptyMap()

                val hasFailures = inlineExampleValidationResults.isNotEmpty() || externalExampleValidationResults.isNotEmpty()

                if(hasFailures) {
                    println()
                    logger.log("=============== Validation Results ===============")

                    printValidationResult(inlineExampleValidationResults, "Inline example")
                    printValidationResult(externalExampleValidationResults, "Example file")

                    return 1
                }
            }

            return 0
        }

        private fun printValidationResult(validationResults: Map<String, Result>, tag: String) {
            if(validationResults.isEmpty())
                return

            validationResults.forEach { (exampleFileName, result) ->
                if (!result.isSuccess()) {
                    logger.log(System.lineSeparator() + "$tag $exampleFileName has the following validation error(s):")
                    logger.log(result.reportString())
                }
            }

            println()
            logger.log("=============== Validation Summary ===============")
            logger.log(Results(validationResults.values.toList()).summary())
            logger.log("""==================================================""")
        }
    }

    @Command(
        name = "interactive",
        mixinStandardHelpOptions = true,
        description = ["Run the example generation interactively"]
    )
    class Interactive : Callable<Unit> {
        @Option(names = ["--contract-file"], description = ["Contract file path"], required = false)
        var contractFile: File? = null

        @Option(
            names = ["--filter-name"],
            description = ["Use only APIs with this value in their name"],
            defaultValue = "\${env:SPECMATIC_FILTER_NAME}"
        )
        var filterName: String = ""

        @Option(
            names = ["--filter-not-name"],
            description = ["Use only APIs which do not have this value in their name"],
            defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}"
        )
        var filterNotName: String = ""

        @Option(names = ["--debug"], description = ["Debug logs"])
        var verbose = false

        var server: ExamplesInteractiveServer? = null

        override fun call() {
            configureLogger(verbose)

            try {
                if (contractFile != null && !contractFile!!.exists())
                    exitWithMessage("Could not find file ${contractFile!!.path}")

                server = ExamplesInteractiveServer("0.0.0.0", 9001, contractFile, filterName, filterNotName)
                addShutdownHook()

                consoleLog(StringLog("Examples Interactive server is running on http://0.0.0.0:9001/_specmatic/examples. Ctrl + C to stop."))
                while (true) sleep(10000)
            } catch (e: Exception) {
                logger.log(exceptionCauseMessage(e))
                exitWithMessage(e.message.orEmpty())
            }
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

private fun configureLogger(verbose: Boolean) {
    val logPrinters = listOf(ConsolePrinter)

    logger = if (verbose)
        Verbose(CompositePrinter(logPrinters))
    else
        NonVerbose(CompositePrinter(logPrinters))
}
