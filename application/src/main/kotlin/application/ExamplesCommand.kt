package application

import io.specmatic.core.Result
import io.specmatic.core.Results
import io.specmatic.core.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.examples.server.ExamplesInteractiveServer
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.validateSingleExample
import io.specmatic.core.examples.server.loadExternalExamples
import io.specmatic.core.filters.ScenarioMetadataFilter
import io.specmatic.core.log.*
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.*
import io.specmatic.mock.ScenarioStub
import picocli.CommandLine.*
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.Callable

@Command(
    name = "examples",
    mixinStandardHelpOptions = true,
    description = ["Generate externalised JSON example files with API requests and responses"],
    subcommands = [ExamplesCommand.Validate::class, ExamplesCommand.Interactive::class]
)
class ExamplesCommand : Callable<Int> {
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

    @Option(names = ["--dictionary"], description = ["External Dictionary File Path, defaults to dictionary.json"])
    var dictionaryFile: File? = null

    @Option(
        names= ["--filter"],
        description = [
            """
Filter tests matching the specified filtering criteria

You can filter tests based on the following keys:
- `METHOD`: HTTP methods (e.g., GET, POST)
- `PATH`: Request paths (e.g., /users, /product)
- `STATUS`: HTTP response status codes (e.g., 200, 400)
- `HEADERS`: Request headers (e.g., Accept, X-Request-ID)
- `QUERY-PARAM`: Query parameters (e.g., status, productId)
- `EXAMPLE-NAME`: Example name (e.g., create-product, active-status)

To specify multiple values for the same filter, separate them with commas. 
For example, to filter by HTTP methods: 
--filter="METHOD=GET,POST"

You can supply multiple filters as well. 
For example:
--filter="METHOD=GET,POST" --filter="PATH=/users"
           """
        ],
        required = false
    )
    var filter: List<String> = emptyList()

    @Option(
        names= ["--filter-not"],
        description = [
            """
Filter tests not matching the specified criteria

This option supports the same filtering keys and syntax as the --filter option.
For example:
--filterNot="STATUS=400" --filterNot="METHOD=PATCH,PUT"
           """
        ],
        required = false
    )
    var filterNot: List<String> = emptyList()

    override fun call(): Int {
        if (contractFile == null) {
            println("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return 1
        }
        if (!contractFile!!.exists()) {
            logger.log("Could not find file ${contractFile!!.path}")
            return 1
        }

        configureLogger(this.verbose)

        try {
            dictionaryFile?.also {
                System.setProperty(SPECMATIC_STUB_DICTIONARY, it.path)
            }

            ExamplesInteractiveServer.generate(
                contractFile!!,
                ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter, filterNot),
                extensive,
            )
        } catch (e: Throwable) {
            logger.log(e)
            return 1
        }

        return 0
    }

    @Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = ["Validate the examples"]
    )
    class Validate : Callable<Int> {
        @Option(
            names= ["--filter"],
            description = [
                """
Filter tests matching the specified filtering criteria

You can filter tests based on the following keys:
- `METHOD`: HTTP methods (e.g., GET, POST)
- `PATH`: Request paths (e.g., /users, /product)
- `STATUS`: HTTP response status codes (e.g., 200, 400)
- `HEADERS`: Request headers (e.g., Accept, X-Request-ID)
- `QUERY-PARAM`: Query parameters (e.g., status, productId)
- `EXAMPLE-NAME`: Example name (e.g., create-product, active-status)

To specify multiple values for the same filter, separate them with commas. 
For example, to filter by HTTP methods: 
--filter="METHOD=GET,POST"

You can supply multiple filters as well. 
For example:
--filter="METHOD=GET,POST" --filter="PATH=/users"
           """
            ],
            required = false
        )
        var filter: List<String> = emptyList()

        @Option(
            names= ["--filter-not"],
            description = [
                """
Filter tests not matching the specified criteria

This option supports the same filtering keys and syntax as the --filter option.
For example:
--filterNot="STATUS=400" --filterNot="METHOD=PATCH,PUT"
           """
            ],
            required = false
        )
        var filterNot: List<String> = emptyList()

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
            if (!contractFile.exists()) {
                logger.log("Could not find file ${contractFile.path}")
                return 1
            }

            configureLogger(this.verbose)

            if (exampleFile != null) {
                try {
                    validateSingleExample(contractFile, exampleFile).throwOnFailure()

                    logger.log("The provided example ${exampleFile.name} is valid.")
                } catch (e: ContractException) {
                    logger.log("The provided example ${exampleFile.name} is invalid. Reason:\n")
                    logger.log(exceptionCauseMessage(e))
                    return 1
                }
            } else {
                val scenarioFilter = ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter, filterNot)

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

                    ExamplesInteractiveServer.validateMultipleExamples(feature, examples = inlineExamples, inline = true, scenarioFilter = scenarioFilter)
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

                    ExamplesInteractiveServer.validateMultipleExamples(feature, examples = externalExamples, scenarioFilter = scenarioFilter)
                } else emptyMap()

                val hasFailures = inlineExampleValidationResults.any { it.value is Result.Failure } || externalExampleValidationResults.any { it.value is Result.Failure }

                printValidationResult(inlineExampleValidationResults, "Inline example")
                printValidationResult(externalExampleValidationResults, "Example file")

                if(hasFailures)
                    return 1
            }

            return 0
        }

        private fun printValidationResult(validationResults: Map<String, Result>, tag: String) {
            if(validationResults.isEmpty())
                return

            val hasFailures = validationResults.any { it.value is Result.Failure }

            val titleTag = tag.split(" ").joinToString(" ") { if(it.isBlank()) it else it.capitalizeFirstChar() }

            if(hasFailures) {
                println()
                logger.log("=============== $titleTag Validation Results ===============")

                validationResults.forEach { (exampleFileName, result) ->
                    if (!result.isSuccess()) {
                        logger.log(System.lineSeparator() + "$tag $exampleFileName has the following validation error(s):")
                        logger.log(result.reportString())
                    }
                }
            }

            println()
            val summaryTitle = "=============== $titleTag Validation Summary ==============="
            logger.log(summaryTitle)
            logger.log(Results(validationResults.values.toList()).summary())
            logger.log("=".repeat(summaryTitle.length))
        }
    }

    @Command(
        name = "interactive",
        mixinStandardHelpOptions = true,
        description = ["Run the example generation interactively"]
    )
    class Interactive : Callable<Unit> {
        @Option(
            names= ["--filter"],
            description = [
                """
Filter tests matching the specified filtering criteria

You can filter tests based on the following keys:
- `METHOD`: HTTP methods (e.g., GET, POST)
- `PATH`: Request paths (e.g., /users, /product)
- `STATUS`: HTTP response status codes (e.g., 200, 400)
- `HEADERS`: Request headers (e.g., Accept, X-Request-ID)
- `QUERY-PARAM`: Query parameters (e.g., status, productId)
- `EXAMPLE-NAME`: Example name (e.g., create-product, active-status)

To specify multiple values for the same filter, separate them with commas. 
For example, to filter by HTTP methods: 
--filter="METHOD=GET,POST"

You can supply multiple filters as well. 
For example:
--filter="METHOD=GET,POST" --filter="PATH=/users"
           """
            ],
            required = false
        )
        var filter: List<String> = emptyList()

        @Option(
            names= ["--filter-not"],
            description = [
                """
Filter tests not matching the specified criteria

This option supports the same filtering keys and syntax as the --filter option.
For example:
--filterNot="STATUS=400" --filterNot="METHOD=PATCH,PUT"
           """
            ],
            required = false
        )
        var filterNot: List<String> = emptyList()

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

        @Option(names = ["--dictionary"], description = ["External Dictionary File Path"])
        var dictFile: File? = null

        @Option(names = ["--testBaseURL"], description = ["The baseURL of system to test"], required = false)
        var testBaseURL: String? = null

        @Option(
            names = ["--allow-only-mandatory-keys-in-json"],
            description = ["Allow only mandatory keys in the json request and response objects"],
            required = false
        )
        var allowOnlyMandatoryKeysInJSONObject: Boolean = false


        var server: ExamplesInteractiveServer? = null

        override fun call() {
            configureLogger(verbose)

            try {
                if (contractFile != null && !contractFile!!.exists())
                    exitWithMessage("Could not find file ${contractFile!!.path}")

                server = ExamplesInteractiveServer(
                    "0.0.0.0",
                    9001,
                    testBaseURL,
                    contractFile,
                    filterName,
                    filterNotName,
                    filter,
                    filterNot,
                    dictFile,
                    allowOnlyMandatoryKeysInJSONObject
                )
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
