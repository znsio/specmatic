package application

import application.example.ExamplesToValidate
import application.example.ValidateCommandOptions
import io.specmatic.core.*
import io.specmatic.core.examples.module.ExampleModule
import io.specmatic.core.examples.module.ExampleValidationModule
import io.specmatic.core.examples.server.ScenarioFilter
import io.specmatic.core.log.CompositePrinter
import io.specmatic.core.log.ConsolePrinter
import io.specmatic.core.log.NonVerbose
import io.specmatic.core.log.Verbose
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.capitalizeFirstChar
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.mock.ScenarioStub
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private const val SUCCESS_EXIT_CODE = 0
private const val FAILURE_EXIT_CODE = 1

@Command(
    name = "examples",
    mixinStandardHelpOptions = true,
    description = ["Validate inline and externalised examples"],
    subcommands = [ExamplesCommand.Validate::class]
)
class ExamplesCommand : Callable<Int> {

    override fun call(): Int {
        logger.log("Please use one of the subcommands. Use --help to view the list of available subcommands.")
        return FAILURE_EXIT_CODE
    }

    @Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = ["Validate the examples"]
    )
    class Validate(validateCommandOptions: ValidateCommandOptions = ValidateCommandOptions()) : Callable<Int> {
        @Mixin
        var options = validateCommandOptions

        private val exampleValidationModule = ExampleValidationModule()

        override fun call(): Int {
            configureLogger(options.verbose)

            if (options.contractFile != null && options.exampleFile != null) return validateExampleFile(options.contractFile!!, options.exampleFile!!)

            if (options.contractFile != null && options.examplesDir != null) {
                val (exitCode, validationResults) = validateExamplesDir(options.contractFile!!, options.examplesDir!!)

                printValidationResult(validationResults, "Example directory")
                if (exitCode == 1) return FAILURE_EXIT_CODE
                if (validationResults.containsOnlyCompleteFailures()) return FAILURE_EXIT_CODE
                return SUCCESS_EXIT_CODE
            }

            if (options.contractFile != null) return validateImplicitExamplesFrom(options.contractFile!!)

            if (options.specsDir != null && options.examplesBaseDir != null) {
                logger.log("- Validating associated examples in the directory: ${options.examplesBaseDir!!.path}")
                logger.newLine()
                val externalExampleValidationResults = validateAllExamplesAssociatedToEachSpecIn(options.specsDir!!, options.examplesBaseDir!!)

                logger.newLine()
                logger.log("- Validating associated examples in the directory: ${options.specsDir!!.path}")
                logger.newLine()
                val implicitExampleValidationResults = validateAllExamplesAssociatedToEachSpecIn(options.specsDir!!, options.specsDir!!)

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

            if (options.specsDir != null) {
                return validateAllExamplesAssociatedToEachSpecIn(options.specsDir!!, options.specsDir!!).exitCode()
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
                exampleValidationModule.validateExample(contractFile, exampleFile).throwOnFailure()
                logger.log("The provided example ${exampleFile.name} is valid.")
                return SUCCESS_EXIT_CODE
            } catch (e: ContractException) {
                logger.log("The provided example ${exampleFile.name} is invalid. Reason:\n")
                logger.log(exceptionCauseMessage(e))
                return FAILURE_EXIT_CODE
            }
        }

        private fun validateExamplesDir(contractFile: File, examplesDir: File): Pair<Int, Map<String, Result>> =
            validateExamplesDir(parseContractFileWithNoMissingConfigWarning(contractFile), examplesDir)

        private fun validateExamplesDir(feature: Feature, examplesDir: File): Pair<Int, Map<String, Result>> {
            val (externalExampleDir, externalExamples) = ExampleModule().loadExternalExamples(examplesDir = examplesDir)
            if (!externalExampleDir.exists()) {
                logger.log("$externalExampleDir does not exist, did not find any files to validate")
                return FAILURE_EXIT_CODE to emptyMap()
            }
            if (externalExamples.isEmpty()) {
                logger.log("No example files found in $externalExampleDir")
                return SUCCESS_EXIT_CODE to emptyMap()
            }
            return SUCCESS_EXIT_CODE to validateExternalExamples(feature, externalExamples)
        }

        private fun validateAllExamplesAssociatedToEachSpecIn(specsDir: File, examplesBaseDir: File): Map<String, Result> {
            var ordinal = 1

            val validationResults = specsDir.walk().filter { it.isFile && it.extension in CONTRACT_EXTENSIONS }.flatMap { specFile ->
                val relativeSpecPath = specsDir.toPath().relativize(specFile.toPath()).toString()
                val associatedExamplesDir =
                    examplesBaseDir.resolve(relativeSpecPath.substringBeforeLast(".").plus("_examples"))

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
            val feature = parseContractFileWithNoMissingConfigWarning(contractFile)

            val (validateInline, validateExternal) = getValidateInlineAndValidateExternalFlags()

            val inlineExampleValidationResults = if (!validateInline) emptyMap()
            else validateInlineExamples(feature)

            val externalExampleValidationResults = if (!validateExternal) emptyMap()
            else {
                val (exitCode, validationResults)
                        = validateExamplesDir(feature, ExampleModule().defaultExternalExampleDirFrom(contractFile))
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
            return exampleValidationModule.validateInlineExamples(
                feature,
                examples = feature.stubsFromExamples.mapValues { (_, stub) ->
                    stub.map { (request, response) ->
                        ScenarioStub(request, response)
                    }
                },
                scenarioFilter = ScenarioFilter(options.filterName, options.filterNotName, options.filter)
            )
        }

        private fun validateExternalExamples(feature: Feature, externalExamples: List<File>): Map<String, Result> {
            return exampleValidationModule.validateExamples(
                feature,
                examples = externalExamples,
                scenarioFilter = ScenarioFilter(options.filterName, options.filterNotName, options.filter)
            )
        }

        private fun getValidateInlineAndValidateExternalFlags(): Pair<Boolean, Boolean> {
            return when(options.examplesToValidate) {
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

    }

}

private fun configureLogger(verbose: Boolean) {
    val logPrinters = listOf(ConsolePrinter)

    logger = if (verbose)
        Verbose(CompositePrinter(logPrinters))
    else
        NonVerbose(CompositePrinter(logPrinters))
}
