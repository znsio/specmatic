package application

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.*
import io.specmatic.core.examples.server.ExamplesInteractiveServer
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.externaliseInlineExamples
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExamplesDirPath
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExamplesFromDir
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExistingExampleFiles
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.validateExample
import io.specmatic.core.examples.server.defaultExternalExampleDirFrom
import io.specmatic.core.examples.server.loadExternalExamples
import io.specmatic.core.log.*
import io.specmatic.core.pattern.*
import io.specmatic.core.utilities.*
import io.specmatic.core.value.*
import io.specmatic.mock.ScenarioStub
import io.specmatic.test.traverse
import picocli.CommandLine.*
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private const val SUCCESS_EXIT_CODE = 0
private const val FAILURE_EXIT_CODE = 1

@Command(
    name = "examples",
    mixinStandardHelpOptions = true,
    description = ["Generate externalised JSON example files with API requests and responses"],
    subcommands = [
        ExamplesCommand.Validate::class,
        ExamplesCommand.Interactive::class,
        ExamplesCommand.Transform::class,
        ExamplesCommand.Export::class,
        ExamplesCommand.ExampleToDictionary::class
    ]
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

    @Option(
        names = ["--allow-only-mandatory-keys-in-payload"],
        description = ["Generate examples with only mandatory keys in the json request and response payloads"],
        required = false
    )
    var allowOnlyMandatoryKeysInJSONObject: Boolean = false

    override fun call(): Int {
        if (contractFile == null) {
            println("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return FAILURE_EXIT_CODE
        }
        if (!contractFile!!.exists()) {
            logger.log("Could not find file ${contractFile!!.path}")
            return FAILURE_EXIT_CODE
        }

        configureLogger(this.verbose)

        try {
            dictionaryFile?.also {
                System.setProperty(SPECMATIC_STUB_DICTIONARY, it.path)
            }

            ExamplesInteractiveServer.generate(
                contractFile!!,
                ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter, filterNot),
                extensive, allowOnlyMandatoryKeysInJSONObject
            )
        } catch (e: Throwable) {
            logger.log(e)
            return FAILURE_EXIT_CODE
        }

        return SUCCESS_EXIT_CODE
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

        @Option(names = ["--contract-file", "--spec-file"], description = ["Contract file path"], required = false)
        var contractFile: File? = null

        @Option(names = ["--example-file"], description = ["Example file path"], required = false)
        val exampleFile: File? = null

        @Option(names = ["--examples-dir"], description = ["External examples directory path for a single API specification (If you are not following the default naming convention for external examples directory)"], required = false)
        val examplesDir: File? = null

        @Option(names = ["--specs-dir"], description = ["Directory with the API specification files"], required = false)
        val specsDir: File? = null

        @Option(
            names = ["--examples-base-dir"],
            description = ["Base directory which contains multiple external examples directories each named as per the Specmatic naming convention to associate them with the corresponding API specification"],
            required = false
        )
        val examplesBaseDir: File? = null

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
            configureLogger(this.verbose)

            if (contractFile != null && exampleFile != null) return validateExampleFile(contractFile!!, exampleFile)

            if (contractFile != null && examplesDir != null) {
                val (exitCode, validationResults) = validateExamplesDir(contractFile!!, examplesDir)

                printValidationResult(validationResults, "Example directory")
                if (exitCode == 1) return FAILURE_EXIT_CODE
                if (validationResults.containsOnlyCompleteFailures()) return FAILURE_EXIT_CODE
                return SUCCESS_EXIT_CODE
            }

            if (contractFile != null) return validateImplicitExamplesFrom(contractFile!!)

            if (specsDir != null && examplesBaseDir != null) {
                val exitCode = validateAllExamplesAssociatedToEachSpecIn(specsDir, examplesBaseDir)
                return exitCode
            }
            if (specsDir != null) {
                val exitCode = validateAllExamplesAssociatedToEachSpecIn(specsDir, specsDir)
                return exitCode
            }

            logger.log("Invalid combination of CLI options. Please refer to the help section using --help command to understand how to use this command")
            return FAILURE_EXIT_CODE
        }

        private fun validateExampleFile(contractFile: File, exampleFile: File): Int {
            if (!contractFile.exists()) {
                logger.log("Could not find file ${contractFile.path}")
                return FAILURE_EXIT_CODE
            }

            try {
                validateExample(contractFile, exampleFile).throwOnFailure()
                logger.log("The provided example ${exampleFile.name} is valid.")
                return SUCCESS_EXIT_CODE
            } catch (e: ContractException) {
                logger.log("The provided example ${exampleFile.name} is invalid. Reason:\n")
                logger.log(exceptionCauseMessage(e))
                return FAILURE_EXIT_CODE
            }
        }

        private fun validateExamplesDir(contractFile: File, examplesDir: File): Pair<Int, Map<String, Result>> {
            val feature = parseContractFileToFeature(contractFile)
            val (externalExampleDir, externalExamples) = loadExternalExamples(examplesDir = examplesDir)
            if (!externalExampleDir.exists()) {
                logger.log("$externalExampleDir does not exist, did not find any files to validate")
                return FAILURE_EXIT_CODE to emptyMap()
            }
            if (externalExamples.none()) {
                logger.log("No example files found in $externalExampleDir")
                return FAILURE_EXIT_CODE to emptyMap()
            }
            return SUCCESS_EXIT_CODE to validateExternalExamples(feature, externalExamples)
        }

        private fun validateAllExamplesAssociatedToEachSpecIn(specsDir: File, examplesBaseDir: File): Int {
            val validationResults = specsDir.walk().filter { it.isFile }.flatMapIndexed { index, it ->
                val associatedExamplesDir = examplesBaseDir.associatedExampleDirFor(it) ?: return@flatMapIndexed emptyList()

                logger.log("${index.inc()}. Validating examples in ${associatedExamplesDir.name} associated to ${it.name}...${System.lineSeparator()}")
                val results = validateExamplesDir(it, associatedExamplesDir).second.entries.map { entry ->
                    entry.toPair()
                }

                printValidationResult(results.toMap(), "The ${associatedExamplesDir.name} Directory")
                logger.log(System.lineSeparator())
                results
            }.toMap()
            logger.log("Summary:")
            printValidationResult(validationResults, "Overall")
            if (validationResults.containsOnlyCompleteFailures()) return FAILURE_EXIT_CODE
            return SUCCESS_EXIT_CODE
        }

        private fun validateImplicitExamplesFrom(contractFile: File): Int {
            val feature = parseContractFileToFeature(contractFile)

            val (validateInline, validateExternal) = getValidateInlineAndValidateExternalFlags()

            val inlineExampleValidationResults = if (!validateInline) emptyMap()
            else validateInlineExamples(feature)

            val externalExampleValidationResults = if (!validateExternal) emptyMap()
            else {
                val (exitCode, validationResults)
                        = validateExamplesDir(contractFile, defaultExternalExampleDirFrom(contractFile))
                if(exitCode == 1) exitProcess(1)
                validationResults
            }

            val hasFailures =
                inlineExampleValidationResults.containsOnlyCompleteFailures() || externalExampleValidationResults.containsOnlyCompleteFailures()

            printValidationResult(inlineExampleValidationResults, "Inline example")
            printValidationResult(externalExampleValidationResults, "Example file")

            if (hasFailures) return FAILURE_EXIT_CODE
            return SUCCESS_EXIT_CODE
        }

        private fun validateInlineExamples(feature: Feature): Map<String, Result> {
            return ExamplesInteractiveServer.validateInlineExamples(
                feature,
                examples = feature.stubsFromExamples.mapValues { (_, stub) ->
                    stub.map { (request, response) ->
                        ScenarioStub(request, response)
                    }
                },
                scenarioFilter = ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter, filterNot)
            )
        }

        private fun validateExternalExamples(feature: Feature, externalExamples: List<File>): Map<String, Result> {
            return ExamplesInteractiveServer.validateExamples(
                feature,
                examples = externalExamples,
                scenarioFilter = ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter, filterNot)
            )
        }

        private fun getValidateInlineAndValidateExternalFlags(): Pair<Boolean, Boolean> {
            return when {
                !Flags.getBooleanValue("VALIDATE_INLINE_EXAMPLES") && !Flags.getBooleanValue(
                    "IGNORE_INLINE_EXAMPLES"
                ) -> true to true

                else -> Flags.getBooleanValue("VALIDATE_INLINE_EXAMPLES") to Flags.getBooleanValue("IGNORE_INLINE_EXAMPLES")
            }
        }

        private fun printValidationResult(validationResults: Map<String, Result>, tag: String) {
            if (validationResults.isEmpty())
                return

            val titleTag = tag.split(" ").joinToString(" ") { if (it.isBlank()) it else it.capitalizeFirstChar() }

            if (validationResults.containsFailuresOrPartialFailures()) {
                println()
                logger.log("=============== $titleTag Validation Results ===============")

                validationResults.forEach { (exampleFileName, result) ->
                    if (!result.isSuccess()) {
                        val errorPrefix = if (result.isPartialFailure()) "Warning" else "Error"

                        logger.log("\n$errorPrefix(s) found in the following $tag $exampleFileName:")
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

        private fun Map<String, Result>.containsOnlyCompleteFailures(): Boolean {
            return this.any { it.value is Result.Failure && !it.value.isPartialFailure() }
        }

        private fun Map<String, Result>.containsFailuresOrPartialFailures(): Boolean {
            return this.any { it.value is Result.Failure }
        }

        private fun File.associatedExampleDirFor(specFile: File): File? {
            return this.walk().firstOrNull { exampleDir ->
                exampleDir.isFile.not() && exampleDir.nameWithoutExtension == "${specFile.nameWithoutExtension}_examples"
            }
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
            names = ["--allow-only-mandatory-keys-in-payload"],
            description = ["Generate examples with only mandatory keys in the json request and response payloads"],
            required = false
        )
        var allowOnlyMandatoryKeysInJSONObject: Boolean = false


        var server: ExamplesInteractiveServer? = null

        override fun call() {
            configureLogger(verbose)

            try {
                if (contractFile != null && !contractFile!!.exists())
                    exitWithMessage("Could not find file ${contractFile!!.path}")

                val host = "0.0.0.0"
                val port = 9001
                server = ExamplesInteractiveServer(
                    host,
                    port,
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

                consoleLog(StringLog("Examples Interactive server is running on ${consolePrintableURL(host, port)}/_specmatic/examples. Ctrl + C to stop."))
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

    @Command(
        name = "transform",
        mixinStandardHelpOptions = true,
        description = ["Transform existing examples"]
    )
    class Transform: Callable<Unit> {
        @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
        lateinit var contractFile: File

        @Option(names = ["--overlay-file"], description = ["Overlay file path"], required = false)
        val overlayFile: File? = null

        @Option(names = ["--examples-dir"], description = ["Directory where existing examples reside"], required = true)
        lateinit var examplesDir: File

        @Option(names = ["--only-mandatory-keys-in-payload"], description = ["Transform existing examples so that they contain only mandatory keys in payload"], required = false)
        var allowOnlyMandatoryKeysInPayload: Boolean = false

        @Option(names = ["--debug"], description = ["Debug Logs"])
        var verbose: Boolean = false

        override fun call() {
            configureLogger(verbose)

            if(allowOnlyMandatoryKeysInPayload) {
                ExamplesInteractiveServer.transformExistingExamples(
                    contractFile,
                    overlayFile,
                    examplesDir
                )
            } else {
                logger.log("Please choose one of the transformations from the available command-line parameters.")
            }
        }
    }

    @Command(
        name = "export",
        mixinStandardHelpOptions = true,
        description = ["Export the inline examples from the contract file"]
    )
    class Export: Callable<Unit> {
        @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
        lateinit var contractFile: File

        override fun call() {
            try {
                val examplesDir = externaliseInlineExamples(contractFile)
                consoleLog("${System.lineSeparator()}The inline examples were successfully exported to $examplesDir")
                exitProcess(0)
            } catch(e: Exception) {
                exitWithMessage("Failed while exporting the inline examples from ${contractFile.nameWithoutExtension}:\n${e.message}")
            }
        }
    }

    @Command(
        name = "dictionary",
        mixinStandardHelpOptions = true,
        description = ["Generate Dictionary from external example files"]
    )
    class ExampleToDictionary: Callable<Unit> {
        @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
        lateinit var contractFile: File

        @Option(names = ["--base"], description = ["Base dictionary"], required = false)
        private var baseDictionaryFile: File? = null

        @Option(names = ["--out", "o"], description = ["Output file path, defaults to contractfile_dictionary.json"], required = false)
        private var outputFilePath: File? = null

        override fun call() {
            val baseDictionary = getBaseDictionary()
            val feature = parseContractFileToFeature(contractFile)
            val examples = getExamplesDirPath(contractFile).getExamplesFromDir()
            val dictionary = mutableMapOf<String, Value>()
            var examplesCount = 0

            feature.scenarios.forEach { scenario ->
                val matchingExamples = getExistingExampleFiles(feature, scenario, examples)
                examplesCount += matchingExamples.size
                matchingExamples.map { (example, _) ->
                    val exampleDictionary = example.toDictionary(scenario)
                    dictionary.putAll(exampleDictionary)
                }
            }

            if (dictionary.isEmpty()) {
                consoleLog("\nNo Values created in dictionary, Processed $examplesCount examples")
            }

            val dictionaryFile = outputFilePath ?: File(contractFile.parentFile, "${contractFile.nameWithoutExtension}_dictionary.json")
            val combinedDictionary = baseDictionary.plus(dictionary)
            dictionaryFile.writeText(JSONObjectValue(combinedDictionary).toStringLiteral())
            consoleLog("\nDictionary written to ${dictionaryFile.canonicalPath}")
        }

        private fun getBaseDictionary(): Map<String, Value> {
            return baseDictionaryFile?.let {
                parsedJSONObject(it.readText()).jsonObject
            } ?: emptyMap()
        }

        private fun ExampleFromFile.toDictionary(scenario: Scenario): Map<String, Value> {
            val requestPattern = resolvedHop(scenario.httpRequestPattern.body, scenario.resolver)
            val responsePattern = resolvedHop(scenario.httpResponsePattern.body, scenario.resolver)

            val updatedResolver = scenario.resolver.ignoreAll()
            val requestDictionary = this.request.body.toDictionary(requestPattern, updatedResolver)
            val responseDictionary = this.response.body.toDictionary(responsePattern, updatedResolver)
            return requestDictionary.plus(responseDictionary)
        }

        private fun Value.toDictionary(pattern: Pattern, resolver: Resolver): Map<String, Value> {
            return pattern.getTypeAlias(this, resolver)?.let {
                this.traverse(
                    prefix = it,
                    onScalar = { scalar, prefix -> scalar.handleScalar(this, pattern, prefix, resolver) },
                    onComposite = { composite, prefix -> composite.handleComposite(this, pattern, prefix, resolver) },
                    onAssert = { _, _ -> emptyMap() }
                )
            }.orEmpty()
        }

        private fun Value.handleComposite(patternValue: Value, pattern: Pattern, prefix: String, resolver: Resolver): Map<String, Value> {
            val key = prefix.split(".").last()
            return pattern.ifKeyIsNewSchema(patternValue, key, resolver) { subPattern ->
                this.toDictionary(subPattern, resolver)
            } ?: this.traverse(
                prefix = "$prefix[*]",
                onScalar = { scalar, innerPrefix -> scalar.handleScalar(patternValue, pattern, innerPrefix, resolver) },
                onAssert = { _, _ -> emptyMap() }
            )
        }

        private fun Value.handleScalar(patternValue: Value, pattern: Pattern, prefix: String, resolver: Resolver): Map<String, Value> {
            val key = prefix.split(".").last()
            val parentPatternKey = prefix.split(".").getOrElse(1) { prefix }

            val parentPattern = pattern.getKeySchema(patternValue, parentPatternKey, resolver)
            val keyPattern = parentPattern?.let { resolvedHop(it, resolver).getKeySchema(patternValue, key, resolver) }

            if (parentPattern is DeferredPattern || keyPattern == null) return emptyMap()
            if (keyPattern.matches(this, resolver.validateAll()) is Result.Success) {
                return mapOf(prefix to this)
            }

            val defaultValue = keyPattern.toDefault().takeIf { it !is NullValue } ?: return emptyMap()
            return mapOf(prefix to defaultValue)
        }

        private fun <T> Pattern.ifKeyIsNewSchema(value: Value, key: String, resolver: Resolver, block: (pattern: Pattern) -> T): T? {
            val pattern = this.getKeySchema(value, key, resolver)
            return if (pattern is DeferredPattern) {
                block(resolvedHop(pattern, resolver))
            } else null
        }

        private fun Pattern.getKeySchema(value: Value, key: String, resolver: Resolver): Pattern? {
            return when(this) {
                is ListPattern -> {
                    val patternValue = value.getInnerValueIfList()
                    this.pattern.getKeySchema(patternValue, key, resolver)
                }
                is JSONObjectPattern -> {
                    val pattern = this.pattern[key] ?: this.pattern["$key?"] ?: return null
                    pattern.getKeySchema(value, key, resolver)
                }
                is AnyPattern -> this.pattern.firstOrNull { it.matches(value, resolver) is Result.Success }?.getKeySchema(value, key, resolver)
                else -> this
            }
        }

        private fun Value.getInnerValueIfList(): Value {
            return when(this) {
                is JSONArrayValue -> this.list.first()
                else -> this
            }
        }

        private fun Pattern.getTypeAlias(value: Value, resolver: Resolver): String? {
            return when(this) {
                is ListPattern -> this.typeAlias ?: this.pattern.getTypeAlias(value, resolver)
                is AnyPattern -> this.pattern.firstOrNull { it.matches(value, resolver) is Result.Success }?.getTypeAlias(value, resolver)
                else -> this.typeAlias
            }?.let { withoutPatternDelimiters(it) }
        }

        private fun Resolver.ignoreAll(): Resolver {
            return this.copy(
                patternMatchStrategy = matchAnything,
                findKeyErrorCheck = findKeyErrorCheck.copy(unexpectedKeyCheck = IgnoreUnexpectedKeys)
            )
        }

        private fun Resolver.validateAll(): Resolver {
            return this.copy(
                patternMatchStrategy = actualMatch,
                findKeyErrorCheck = findKeyErrorCheck.copy(unexpectedKeyCheck = ValidateUnexpectedKeys)
            )
        }

        private fun Pattern.toDefault(): Value {
            return when(this) {
                is JSONObjectPattern -> JSONObjectValue(this.pattern.map { (key, pattern) -> withoutOptionality(key) to pattern.toDefault() }.toMap())
                is ListPattern -> this.pattern.toDefault()
                is AnyPattern -> this.pattern.firstOrNull()?.toDefault() ?: NullValue
                is StringPattern -> StringValue("TODO")
                is NumberPattern -> NumberValue(999)
                is BooleanPattern -> BooleanValue(false)
                else -> NullValue
            }
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
