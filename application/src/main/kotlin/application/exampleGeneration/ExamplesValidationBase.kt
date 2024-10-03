package application.exampleGeneration

import io.specmatic.core.Result
import io.specmatic.core.log.logger
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.Callable

abstract class ExamplesValidationBase<Feature, Scenario>(private val common: ExamplesCommon<Feature, Scenario>): Callable<Int> {
    @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
    private lateinit var contractFile: File

    @Option(names = ["--example-file"], description = ["Example file path"], required = false)
    private val exampleFile: File? = null

    @Option(names = ["--filter-name"], description = ["Use only APIs with this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NAME}")
    var filterName: String = ""

    @Option(names = ["--filter-not-name"], description = ["Use only APIs which do not have this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}")
    var filterNotName: String = ""

    @Option(names = ["--debug"], description = ["Debug logs"])
    private var verbose = false

    abstract var validateExternal: Boolean
    abstract var validateInline: Boolean
    abstract var extensive: Boolean

    override fun call(): Int {
        common.configureLogger(this.verbose)

        if (!contractFile.exists()) {
            logger.log("Could not find Contract file ${contractFile.path}")
            return 1
        }

        if (contractFile.extension !in common.contractFileExtensions) {
            logger.log("Invalid Contract file ${contractFile.path} - File extension must be one of ${common.contractFileExtensions.joinToString()}")
            return 1
        }

        try {
            exampleFile?.let { exFile ->
                if (!exFile.exists()) {
                    logger.log("Could not find Example file ${exFile.path}")
                    return 1
                }

                if (exFile.extension !in common.exampleFileExtensions) {
                    logger.log("Invalid Example file ${exFile.path} - File extension must be one of ${common.exampleFileExtensions.joinToString()}")
                    return 1
                }

                val validation = validateExampleFile(exFile, contractFile)
                return getExitCode(validation)
            }

            val inlineResults = if(validateInline) validateInlineExamples(contractFile) else emptyList()
            val externalResults = if(validateExternal) validateExternalExamples(contractFile) else emptyList()

            logValidationResult(inlineResults, externalResults)
            return getExitCode(inlineResults.plus(externalResults))
        } catch (e: Throwable) {
            logger.log("Validation failed with error: ${e.message}")
            logger.debug(e)
            return 1
        }
    }

    // HOOKS
    open fun validateInlineExamples(feature: Feature): List<Pair<String, Result>> {
        return emptyList()
    }

    // VALIDATION METHODS
    private fun validateExampleFile(exampleFile: File, contractFile: File): ExampleValidationResult {
        val feature = common.contractFileToFeature(contractFile)
        return validateExternalExample(exampleFile, feature)
    }

    private fun validateExternalExample(exampleFile: File, feature: Feature): ExampleValidationResult {
        return try {
            val result = common.validateExternalExample(feature, exampleFile)
            ExampleValidationResult(exampleFile.absolutePath, result.second, ExampleType.EXTERNAL)
        } catch (e: Throwable) {
            logger.log("Example validation failed with error: ${e.message}")
            logger.debug(e)
            ExampleValidationResult(exampleFile.absolutePath, Result.Failure(e.message.orEmpty()), ExampleType.EXTERNAL)
        }
    }

    private fun getFilteredFeature(contractFile: File): Feature {
        val scenarioFilter = ScenarioFilter(filterName, filterNotName, extensive)
        val feature = common.contractFileToFeature(contractFile)
        val filteredScenarios = common.getFilteredScenarios(feature, scenarioFilter)

        return common.updateFeatureForValidation(feature, filteredScenarios)
    }

    private fun validateExternalExamples(contractFile: File): List<ExampleValidationResult> {
        val examplesDir = common.getExamplesDirectory(contractFile)
        val exampleFiles = common.getExternalExampleFiles(examplesDir)
        val feature = getFilteredFeature(contractFile)

        return exampleFiles.mapIndexed { index, it ->
            validateExternalExample(it, feature).also {
                it.logErrors(index.inc())
                common.logSeparator(75)
            }
        }
    }

    private fun validateInlineExamples(contractFile: File): List<ExampleValidationResult> {
        val feature = getFilteredFeature(contractFile)
        return validateInlineExamples(feature).mapIndexed { index, it ->
            ExampleValidationResult(it.first, it.second, ExampleType.INLINE).also {
                it.logErrors(index.inc())
                common.logSeparator(75)
            }
        }
    }

    // HELPER METHODS
    private fun getExitCode(validations: List<ExampleValidationResult>): Int {
        return if (validations.any { !it.result.isSuccess() }) 1 else 0
    }

    private fun getExitCode(validation: ExampleValidationResult): Int {
        return if (!validation.result.isSuccess()) 1 else 0
    }

    private fun logValidationResult(inlineResults: List<ExampleValidationResult>, externalResults: List<ExampleValidationResult>) {
        logResultSummary(inlineResults, ExampleType.INLINE)
        logResultSummary(externalResults, ExampleType.EXTERNAL)
    }

    private fun logResultSummary(results: List<ExampleValidationResult>, type: ExampleType) {
        if (results.isNotEmpty()) {
            val successCount = results.count { it.result.isSuccess() }
            val failureCount = results.size - successCount
            printSummary(type, successCount, failureCount)
        }
    }

    private fun printSummary(type: ExampleType, successCount: Int, failureCount: Int) {
        common.logFormattedOutput(
            header = "$type Examples Validation Summary",
            summary = "$successCount example(s) are valid. $failureCount example(s) are invalid",
            note = ""
        )
    }

    private fun ExampleValidationResult.logErrors(index: Int? = null) {
        val prefix = index?.let { "$it. " } ?: ""

        if (this.result.isSuccess()) {
            return logger.log("$prefix${this.exampleName} is valid")
        }

        logger.log("\n$prefix${this.exampleName} has the following validation error(s):")
        logger.log(this.result.reportString())
    }
}

enum class ExampleType(val value: String) {
    INLINE("Inline"),
    EXTERNAL("External");

    override fun toString(): String {
        return this.value
    }
}

data class ExampleValidationResult(val exampleName: String, val result: Result, val type: ExampleType)
