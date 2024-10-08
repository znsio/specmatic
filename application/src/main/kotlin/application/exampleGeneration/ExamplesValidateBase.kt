package application.exampleGeneration

import io.specmatic.core.Result
import io.specmatic.core.log.logger
import picocli.CommandLine.Option
import java.io.File

abstract class ExamplesValidateBase<Feature, Scenario>(
    override val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>,
    private val validationStrategy: ExamplesValidationStrategy<Feature, Scenario>
): ExamplesBase<Feature, Scenario>(featureStrategy) {
    @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
    override var contractFile: File? = null

    @Option(names = ["--example-file"], description = ["Example file path"], required = false)
    private val exampleFile: File? = null

    abstract var validateExternal: Boolean
    abstract var validateInline: Boolean
    abstract var extensive: Boolean

    override fun execute(contract: File?): Int {
        if (contract == null) {
            logger.log("No contract file provided, please provide a contract file. Use --help for more details.")
            return 1
        }

        try {
            exampleFile?.let { exFile ->
                if (!exFile.exists()) {
                    logger.log("Could not find Example file ${exFile.path}")
                    return 1
                }

                if (exFile.extension !in featureStrategy.exampleFileExtensions) {
                    logger.log("Invalid Example file ${exFile.path} - File extension must be one of ${featureStrategy.exampleFileExtensions.joinToString()}")
                    return 1
                }

                val validation = validateExampleFile(exFile, contract)
                return getExitCode(validation)
            }

            val inlineResults = if (validateInline) validateInlineExamples(contract) else emptyList()
            val externalResults = if (validateExternal) validateExternalExamples(contract) else emptyList()

            logValidationResult(inlineResults, externalResults)
            return getExitCode(inlineResults + externalResults)
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
    private fun getFilteredFeature(contractFile: File): Feature {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val filteredScenarios = getFilteredScenarios(feature)
        return validationStrategy.updateFeatureForValidation(feature, filteredScenarios)
    }

    private fun validateExampleFile(exampleFile: File, contractFile: File): ExampleValidationResult {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        return validateExternalExample(exampleFile, feature)
    }

    private fun validateExternalExample(exampleFile: File, feature: Feature): ExampleValidationResult {
        return try {
            val result = validationStrategy.validateExternalExample(feature, exampleFile)
            ExampleValidationResult(exampleFile, result.second)
        } catch (e: Throwable) {
            logger.log("Example validation failed with error: ${e.message}")
            logger.debug(e)
            ExampleValidationResult(exampleFile, Result.Failure(e.message.orEmpty()))
        }
    }

    private fun validateInlineExamples(contractFile: File): List<ExampleValidationResult> {
        val feature = getFilteredFeature(contractFile)
        return validateInlineExamples(feature).mapIndexed { index, it ->
            ExampleValidationResult(it.first, it.second, ExampleType.INLINE).also {
                it.logErrors(index.inc())
                logSeparator(75)
            }
        }
    }

    private fun validateExternalExamples(contractFile: File): List<ExampleValidationResult> {
        val examplesDir = getExamplesDirectory(contractFile)
        val exampleFiles = getExternalExampleFiles(examplesDir)
        val feature = getFilteredFeature(contractFile)

        return exampleFiles.mapIndexed { index, it ->
            validateExternalExample(it, feature).also {
                it.logErrors(index.inc())
                logSeparator(75)
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
        if (inlineResults.isNotEmpty()) {
            val successCount = inlineResults.count { it.result.isSuccess() }
            val failureCount = inlineResults.size - successCount
            logResultSummary(ExampleType.INLINE, successCount, failureCount)
        }

        if (externalResults.isNotEmpty()) {
            val successCount = externalResults.count { it.result.isSuccess() }
            val failureCount = externalResults.size - successCount
            logResultSummary(ExampleType.EXTERNAL, successCount, failureCount)
        }
    }

    private fun logResultSummary(type: ExampleType, successCount: Int, failureCount: Int) {
        logFormattedOutput(
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

data class ExampleValidationResult(val exampleName: String, val result: Result, val type: ExampleType, val exampleFIle: File? = null) {
    constructor(exampleFile: File, result: Result) : this(exampleFile.nameWithoutExtension, result, ExampleType.EXTERNAL, exampleFile)
}

interface ExamplesValidationStrategy<Feature, Scenario> {
    fun updateFeatureForValidation(feature: Feature, filteredScenarios: List<Scenario>): Feature

    fun validateExternalExample(feature: Feature, exampleFile: File): Pair<String, Result>
}
