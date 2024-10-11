package application.exampleGeneration

import io.specmatic.core.Result
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.log.consoleLog
import picocli.CommandLine.Option
import java.io.File

abstract class ExamplesValidateBase<Feature, Scenario>(
    override val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>,
    private val validationStrategy: ExamplesValidationStrategy<Feature, Scenario>
): ExamplesBase<Feature, Scenario>(featureStrategy) {
    @Option(names = ["--contract-file"], description = ["Contract file path"], required = true)
    public override var contractFile: File? = null

    @Option(names = ["--example-file"], description = ["Example file path"], required = false)
    var exampleFile: File? = null

    abstract var validateExternal: Boolean
    abstract var validateInline: Boolean

    override fun execute(contract: File?): Int {
        if (contract == null) {
            consoleLog("No contract file provided, please provide a contract file. Use --help for more details.")
            return 1
        }

        try {
            exampleFile?.let { exFile ->
                return validateSingleExampleFile(exFile, contract)
            }

            val inlineResults = if (validateInline)
                validateInlineExamples(contract).logValidationResult()
            else emptyList()

            val externalResults = if (validateExternal)
                validateExternalExamples(contract).logValidationResult()
            else emptyList()

            return externalResults.getExitCode(inlineResults)
        } catch (e: Throwable) {
            consoleLog("Validation failed with error: ${e.message}")
            consoleDebug(e)
            return 1
        }
    }

    // VALIDATION METHODS
    private fun getFilteredFeature(contractFile: File): Feature {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val filteredScenarios = getFilteredScenarios(feature)
        return validationStrategy.updateFeatureForValidation(feature, filteredScenarios)
    }

    private fun validateSingleExampleFile(exampleFile: File, contractFile: File): Int {
        getValidatedExampleFileOrNull(exampleFile)?.let {
            val feature = featureStrategy.contractFileToFeature(contractFile)
            return validateExternalExample(exampleFile, feature).let {
                it.logErrors()
                it.getExitCode()
            }
        } ?: return 1
    }

    private fun validateExternalExample(exampleFile: File, feature: Feature): ExampleValidationResult {
        return try {
            val result = validationStrategy.validateExternalExample(feature, exampleFile)
            ExampleValidationResult(exampleFile, result.second)
        } catch (e: Throwable) {
            consoleLog("Example validation failed with error: ${e.message}")
            consoleDebug(e)
            ExampleValidationResult(exampleFile, Result.Failure(e.message.orEmpty()))
        }
    }

    private fun validateInlineExamples(contractFile: File): List<ExampleValidationResult> {
        val feature = getFilteredFeature(contractFile)
        return validationStrategy.validateInlineExamples(feature).mapIndexed { index, it ->
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
            return consoleLog("$prefix${this.exampleFile?.name ?: this.exampleName} is valid")
        }

        consoleLog("\n$prefix${this.exampleFile?.name ?: this.exampleName} has the following validation error(s):")
        consoleLog(this.result.reportString())
    }

    private fun List<ExampleValidationResult>.logValidationResult(): List<ExampleValidationResult> {
        if (this.isNotEmpty()) {
            require(this.all { it.type == this.first().type }) {
                "All example validation results must be of the same type"
            }

            val successCount = this.count { it.result.isSuccess() }
            val failureCount = this.size - successCount
            logResultSummary(this.first().type, successCount, failureCount)
        }

        return this
    }

    private fun List<ExampleValidationResult>.getExitCode(other: List<ExampleValidationResult>): Int {
        return if (this.any { !it.result.isSuccess() } || other.any { !it.result.isSuccess() }) 1 else 0
    }

    private fun ExampleValidationResult.getExitCode(): Int {
        return if (!this.result.isSuccess()) 1 else 0
    }
}

enum class ExampleType(val value: String) {
    INLINE("Inline"),
    EXTERNAL("External");

    override fun toString(): String {
        return this.value
    }
}

data class ExampleValidationResult(val exampleName: String, val result: Result, val type: ExampleType, val exampleFile: File? = null) {
    constructor(exampleFile: File, result: Result) : this(exampleFile.nameWithoutExtension, result, ExampleType.EXTERNAL, exampleFile)
}

interface ExamplesValidationStrategy<Feature, Scenario> {
    fun updateFeatureForValidation(feature: Feature, filteredScenarios: List<Scenario>): Feature

    fun validateExternalExample(feature: Feature, exampleFile: File): Pair<String, Result>

    fun validateInlineExamples(feature: Feature): List<Pair<String, Result>> { return emptyList() }
}
