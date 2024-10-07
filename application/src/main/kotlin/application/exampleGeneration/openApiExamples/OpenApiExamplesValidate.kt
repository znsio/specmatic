package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesValidateCommon
import application.exampleGeneration.ExamplesValidateBase
import io.specmatic.core.*
import io.specmatic.core.utilities.capitalizeFirstChar
import io.specmatic.mock.NoMatchingScenario
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpStub
import io.specmatic.stub.HttpStubData
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(name = "validate", description = ["Validate OpenAPI inline and external examples"])
class OpenApiExamplesValidate: ExamplesValidateBase<Feature, Scenario>(), OpenApiExamplesValidateCommon {
    @Option(names = ["--validate-external"], description = ["Validate external examples, defaults to true"])
    override var validateExternal: Boolean = true

    @Option(names = ["--validate-inline"], description = ["Validate inline examples, defaults to false"])
    override var validateInline: Boolean = false

    override var extensive: Boolean = true
}

interface OpenApiExamplesValidateCommon: ExamplesValidateCommon<Feature, Scenario>, OpenApiExamplesCommon {
    override fun updateFeatureForValidation(feature: Feature, filteredScenarios: List<Scenario>): Feature {
        return feature.copy(scenarios = filteredScenarios)
    }

    override fun validateExternalExample(feature: Feature, exampleFile: File): Pair<String, Result> {
        val examples = mapOf(exampleFile.nameWithoutExtension to listOf(ScenarioStub.readFromFile(exampleFile)))
        return feature.validateMultipleExamples(examples).first()
    }

    private fun getCleanedUpFailure(failureResults: Results, noMatchingScenario: NoMatchingScenario?): Results {
        return failureResults.toResultIfAny().let {
            if (it.reportString().isBlank())
                Results(listOf(Result.Failure(noMatchingScenario?.message ?: "", failureReason = FailureReason.ScenarioMismatch)))
            else
                failureResults
        }
    }

    private fun Feature.validateMultipleExamples(examples: Map<String, List<ScenarioStub>>, inline: Boolean = false): List<Pair<String, Result>> {
        val results = examples.map { (name, exampleList) ->
            val results = exampleList.mapNotNull { example ->
                try {
                    this.validateExample(example)
                    Result.Success()
                } catch (e: NoMatchingScenario) {
                    if (inline && !e.results.withoutFluff().hasResults())
                        null
                    else
                        e.results.toResultIfAny()
                }
            }
            name to Result.fromResults(results)
        }

        return results
    }

    private fun Feature.validateExample(scenarioStub: ScenarioStub) {
        val result: Pair<Pair<Result.Success, List<HttpStubData>>?, NoMatchingScenario?> = HttpStub.setExpectation(scenarioStub, this, InteractiveExamplesMismatchMessages)
        val validationResult = result.first
        val noMatchingScenario = result.second

        if (validationResult == null) {
            val failures = noMatchingScenario?.results?.withoutFluff()?.results ?: emptyList()

            val failureResults = getCleanedUpFailure(Results(failures).withoutFluff(), noMatchingScenario)
            throw NoMatchingScenario(
                failureResults,
                cachedMessage = failureResults.report(scenarioStub.request),
                msg = failureResults.report(scenarioStub.request)
            )
        }
    }

    object InteractiveExamplesMismatchMessages : MismatchMessages {
        override fun mismatchMessage(expected: String, actual: String): String {
            return "Specification expected $expected but example contained $actual"
        }

        override fun unexpectedKey(keyLabel: String, keyName: String): String {
            return "${keyLabel.capitalizeFirstChar()} $keyName in the example is not in the specification"
        }

        override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
            return "${keyLabel.capitalizeFirstChar()} $keyName in the specification is missing from the example"
        }
    }
}