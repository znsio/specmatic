package application

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.*
import io.specmatic.core.examples.server.ExamplesInteractiveServer
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.externaliseInlineExamples
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.fixExample
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExamplesDirPath
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExamplesFromDir
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExistingExampleFiles
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.validateExample
import io.specmatic.core.examples.server.FixExampleResult
import io.specmatic.core.examples.server.FixExampleStatus
import io.specmatic.core.examples.server.defaultExternalExampleDirFrom
import io.specmatic.core.examples.server.loadExternalExamples
import io.specmatic.core.log.*
import io.specmatic.core.pattern.*
import io.specmatic.core.utilities.*
import io.specmatic.core.value.*
import io.specmatic.mock.ScenarioStub
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
        ExamplesCommand.ExampleToDictionary::class,
        ExamplesCommand.Fix::class
    ]
)
class ExamplesCommand : Callable<Int> {
    @Option(
        names = ["--filter-name"],
        description = ["Use only APIs with this value in their name"],
        defaultValue = "\${env:SPECMATIC_FILTER_NAME}",
        hidden = true
    )
    var filterName: String = ""

    @Option(
        names = ["--filter-not-name"],
        description = ["Use only APIs which do not have this value in their name"],
        defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}",
        hidden = true
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
--filter="METHOD='GET,POST'"
           """
        ],
        required = false
    )
    var filter: String = ""

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
                ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter),
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
           """
            ],
            required = false
        )
        var filter: String = ""

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
            defaultValue = "\${env:SPECMATIC_FILTER_NAME}",
            hidden = true
        )
        var filterName: String = ""

        @Option(
            names = ["--filter-not-name"],
            description = ["Validate examples of only APIs which do not have this value in their name"],
            defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}",
            hidden = true
        )
        var filterNotName: String = ""

        @Option(
            names = ["--examples-to-validate"],
            description = ["Whether to validate inline, external, or both examples. Options: INLINE, EXTERNAL, BOTH"],
            converter = [ExamplesToValidateConverter::class],
            defaultValue = "BOTH"
        )
        var examplesToValidate: ExamplesToValidate = ExamplesToValidate.BOTH

        enum class ExamplesToValidate { INLINE, EXTERNAL, BOTH }
        class ExamplesToValidateConverter : ITypeConverter<ExamplesToValidate> {
            override fun convert(value: String): ExamplesToValidate {
                return ExamplesToValidate.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid value: $value. Expected one of: ${ExamplesToValidate.entries.joinToString(", ")}")
            }
        }

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
                logger.log("- Validating associated examples in the directory: ${examplesBaseDir.path}")
                logger.newLine()
                val externalExampleValidationResults = validateAllExamplesAssociatedToEachSpecIn(specsDir, examplesBaseDir)

                logger.newLine()
                logger.log("- Validating associated examples in the directory: ${specsDir.path}")
                logger.newLine()
                val implicitExampleValidationResults = validateAllExamplesAssociatedToEachSpecIn(specsDir, specsDir)

                logger.newLine()
                val summaryTitle = "- Validation summary across all example directories:"
                logger.log("_".repeat(summaryTitle.length))
                logger.log("- Validation summary across all example directories:")
                printValidationResult(implicitExampleValidationResults + externalExampleValidationResults, "")

                if (
                    externalExampleValidationResults.exitCode() == FAILURE_EXIT_CODE
                    || implicitExampleValidationResults.exitCode() == FAILURE_EXIT_CODE
                ) {
                    return FAILURE_EXIT_CODE
                }
                return SUCCESS_EXIT_CODE
            }

            if (specsDir != null) {
                return validateAllExamplesAssociatedToEachSpecIn(specsDir, specsDir).exitCode()
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

        private fun validateAllExamplesAssociatedToEachSpecIn(specsDir: File, examplesBaseDir: File): Map<String, Result> {
            var ordinal = 1

            val validationResults = specsDir.walk().filter { it.isFile && it.extension == "yaml" }.flatMap { specFile ->
                val relativeSpecPath = specsDir.toPath().relativize(specFile.toPath()).toString()
                val associatedExamplesDir = examplesBaseDir.resolve(relativeSpecPath.replace(".yaml", "_examples"))

                if (associatedExamplesDir.exists().not() || associatedExamplesDir.isDirectory.not()) {
                    return@flatMap emptyList()
                }

                logger.log("$ordinal. Validating examples in '${associatedExamplesDir}' associated to '$relativeSpecPath'...${System.lineSeparator()}")
                ordinal++

                val results = validateExamplesDir(specFile, associatedExamplesDir).second.entries.map { entry ->
                    entry.toPair()
                }

                printValidationResult(results.toMap(), "")
                logger.log(System.lineSeparator())
                results
            }.toMap()

            logger.log("Summary:")
            printValidationResult(validationResults, "Overall")

            return validationResults
        }

        private fun Map<String, Result>.exitCode(): Int {
            return if (this.containsOnlyCompleteFailures()) FAILURE_EXIT_CODE else SUCCESS_EXIT_CODE
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
                scenarioFilter = ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter)
            )
        }

        private fun validateExternalExamples(feature: Feature, externalExamples: List<File>): Map<String, Result> {
            return ExamplesInteractiveServer.validateExamples(
                feature,
                examples = externalExamples,
                scenarioFilter = ExamplesInteractiveServer.ScenarioFilter(filterName, filterNotName, filter)
            )
        }

        private fun getValidateInlineAndValidateExternalFlags(): Pair<Boolean, Boolean> {
            return when(examplesToValidate) {
                ExamplesToValidate.BOTH -> true to true
                ExamplesToValidate.INLINE -> true to false
                ExamplesToValidate.EXTERNAL -> false to true
            }
        }

        private fun printValidationResult(validationResults: Map<String, Result>, tag: String) {
            if (validationResults.isEmpty()) {
                val message = "No associated examples found."
                logger.log("=".repeat(message.length))
                logger.log(message)
                logger.log("=".repeat(message.length))
                return
            }

            val titleTag = tag.split(" ").joinToString(" ") { if (it.isBlank()) it else it.capitalizeFirstChar() }

            if (validationResults.containsFailuresOrPartialFailures()) {
                println()
                logger.log("=============== $titleTag Validation Results ===============")

                validationResults.forEach { (exampleFileName, result) ->
                    if (!result.isSuccess()) {
                        val errorPrefix = if (result.isPartialFailure()) "Warning" else "Error"

                        logger.log("\n$errorPrefix(s) found in the example file - '$exampleFileName':")
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
--filter="METHOD='GET,POST'"
           """
            ],
            required = false
        )
        var filter: String = ""

        @Option(names = ["--contract-file"], description = ["Contract file path"], required = false)
        var contractFile: File? = null

        @Option(
            names = ["--filter-name"],
            description = ["Use only APIs with this value in their name"],
            defaultValue = "\${env:SPECMATIC_FILTER_NAME}",
            hidden = true
        )
        var filterName: String = ""

        @Option(
            names = ["--filter-not-name"],
            description = ["Use only APIs which do not have this value in their name"],
            defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}",
            hidden = true
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

        @Option(names = ["--out", "--o"], description = ["Output file path, defaults to contractfile_dictionary.json"], required = false)
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
                    val exampleDictionary = exampleToDictionary(example, scenario)
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

        fun exampleToDictionary(example: ExampleFromFile, scenario: Scenario): Map<String, Value> {
            val requestPattern = resolvedHop(scenario.httpRequestPattern.body, scenario.resolver)
            val responsePattern = resolvedHop(scenario.httpResponsePattern.body, scenario.resolver)

            val updatedResolver = scenario.resolver.ignoreAll()
            val requestDictionary = example.request.body.toDictionary(requestPattern, updatedResolver)
            val responseDictionary = example.response.body.toDictionary(responsePattern, updatedResolver)
            return requestDictionary.plus(responseDictionary)
        }

        private fun Pattern.getPatternWithTypeAlias(value: Value, resolver: Resolver): Pattern? {
            return when(this) {
                is ListPattern -> this.copy(typeAlias = this.typeAlias ?: this.pattern.typeAlias)
                is AnyPattern -> this.matchingPatternOrNull(value, resolver)
                else -> this
            }?.takeIf { it.typeAlias != null }
        }

        private fun Resolver.ignoreAll(): Resolver {
            return this.copy(
                findKeyErrorCheck = findKeyErrorCheck.copy(unexpectedKeyCheck = IgnoreUnexpectedKeys, patternKeyCheck = noPatternKeyCheck)
            )
        }

        private fun Resolver.validateAll(): Resolver {
            return this.copy(patternMatchStrategy = actualMatch, findKeyErrorCheck = DefaultKeyCheck)
        }

        private fun Value.toEntry(prefix: String, pattern: Pattern, resolver: Resolver): Map<String, Value> {
            val result = pattern.matches(this, resolver.validateAll())
            return if (result.isSuccess() && pattern is ScalarType) mapOf(prefix to this) else emptyMap()
        }

        private fun Value.toDictionary(pattern: Pattern, resolver: Resolver, prefix: String = "", suffix: String = ""): Map<String, Value> {
            return pattern.getPatternWithTypeAlias(this, resolver)?.let {
                return this.traverse(it, resolver, "$prefix${withoutPatternDelimiters(it.typeAlias ?: "")}$suffix")
            } ?: emptyMap()
        }

        private fun Value.traverse(pattern: Pattern, resolver: Resolver, prefix: String = ""): Map<String, Value> {
            return when {
                this is JSONObjectValue && pattern is JSONObjectPattern -> this.traverse(pattern, resolver, prefix)
                this is JSONArrayValue && pattern is ListPattern -> this.traverse(pattern, resolver, prefix)
                else -> this.toEntry(prefix, pattern, resolver)
            }
        }

        private fun JSONObjectValue.traverse(pattern: JSONObjectPattern, resolver: Resolver, prefix: String): Map<String, Value> {
            return this.jsonObject.flatMap { (key, value) ->
                val keyPattern = pattern.getKeySchema(this, resolver, key) ?: return@flatMap emptyList()
                keyPattern.ifNewSchema {
                    value.toDictionary(resolvedHop(keyPattern, resolver), resolver).entries
                } ?: value.traverse(keyPattern, resolver, "$prefix.$key").entries
            }.associate { it.toPair() }
        }

        private fun JSONArrayValue.traverse(pattern: ListPattern, resolver: Resolver, prefix: String): Map<String, Value> {
            val resolvedPattern = resolvedHop(pattern.pattern, resolver)
            return pattern.ifNewSchema {
                this.list.fold(emptyMap()) { acc, value -> acc.plus(value.toDictionary(resolvedPattern, resolver, suffix = "[*]")) }
            } ?: this.list.fold(emptyMap()) { acc, value -> acc.plus(value.traverse(resolvedPattern, resolver, prefix = "$prefix[*]")) }
        }

        private fun <T> Pattern.ifNewSchema(block: () -> T): T? {
            return when (this) {
                is DeferredPattern -> block()
                is ListPattern -> this.pattern.ifNewSchema(block)
                else -> null
            }
        }

        private fun Pattern.getKeySchema(value: Value, resolver: Resolver, key: String): Pattern? {
            return when(this) {
                is JSONObjectPattern -> this.pattern[key] ?: this.pattern["$key?"]
                is AnyPattern -> this.matchingPatternOrNull(value, resolver)?.getKeySchema(value,resolver, key)
                else -> null
            }
        }

        private fun AnyPattern.matchingPatternOrNull(value: Value, resolver: Resolver): Pattern? {
            return when {
                this.pattern.size > 1 -> this.getMatchingPattern(value, resolver)
                else -> this.getUpdatedPattern(resolver).firstOrNull()
            }
        }
    }

    @Command(
        name = "fix",
        mixinStandardHelpOptions = true,
        description = ["Fix the invalid external examples"]
    )
    class Fix: Callable<Int> {

        @Option(names = ["--spec-file"], description = ["Specification file path"], required = true)
        lateinit var specFile: File

        @Option(names = ["--examples"], description = ["Examples directory path"], required = false)
        var examplesDirPath: File? = null

        override fun call(): Int {
            exitIfSpecFileDoesNotExist()

            val feature = parseContractFileToFeature(specFile)
            val examplesDir = examplesDirPath ?: defaultExternalExampleDirFrom(specFile)
            logger.log("Fixing examples in the directory '${examplesDir.name}'...")

            val results = examplesDir.walk().filter { it.isFile && it.extension == "json" }.map { exampleFile ->
                try {
                    fixExample(feature, exampleFile)
                } catch (e: Exception) {
                    FixExampleResult(
                        status = FixExampleStatus.FAILED,
                        exampleFileName = exampleFile.name,
                        error = e
                    )
                }
            }.toList()

            return printFixExamplesOperationResultsAndReturnExitCode(results)
        }

        private fun exitIfSpecFileDoesNotExist() {
            if(specFile.exists().not()) {
                exitWithMessage("Provided specification file ${specFile.name} does not exist.")
            }
        }

        private fun List<FixExampleResult>.with(status: FixExampleStatus): List<FixExampleResult> {
            return this.filter {  it.status == status }
        }

        private fun printFixExamplesOperationResultsAndReturnExitCode(results: List<FixExampleResult>): Int {
            val skippedResults = results.with(status = FixExampleStatus.SKIPPED)
            val successResults = results.with(status = FixExampleStatus.SUCCEDED)
            val failureResults = results.with(status = FixExampleStatus.FAILED)

            if (successResults.isNotEmpty()) {
                logger.log("${System.lineSeparator()}Examples fixed successfully: ")
                successResults.forEachIndexed { index, it ->
                    logger.log("\t${index.inc()}. The example '${it.exampleFileName}' is fixed.")
                }
            }
            if(skippedResults.isNotEmpty()) {
                logger.log("${System.lineSeparator()}Skipped examples: ")
                skippedResults.forEachIndexed { index, it ->
                    logger.log("\t${index.inc()}. Skipping the example '${it.exampleFileName}' as it is already valid.")
                }
            }
            if (failureResults.isNotEmpty()) {
                logger.log("${System.lineSeparator()}Examples for which the fix operation failed: ")
                failureResults.forEachIndexed { index, it ->
                    val errorMessage = exceptionCauseMessage(it.error ?: Exception("Unknown error"))
                    logger.log("\t${index.inc()}. An error occurred while fixing '${it.exampleFileName}': $errorMessage")
                }
            }

            logger.log(System.lineSeparator())
            logger.log("Examples fixed: ${successResults.size}")
            logger.log("Examples skipped: ${skippedResults.size}")
            logger.log("Examples failed to be fixed: ${failureResults.size}")

            if (failureResults.isEmpty()) return SUCCESS_EXIT_CODE
            return FAILURE_EXIT_CODE
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
