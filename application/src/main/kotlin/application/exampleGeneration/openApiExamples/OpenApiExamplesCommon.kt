package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesCommon
import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.*
import io.specmatic.core.utilities.capitalizeFirstChar
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.mock.NoMatchingScenario
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpStub
import io.specmatic.stub.HttpStubData
import java.io.File

class OpenApiExamplesCommon: ExamplesCommon<Feature, Scenario> {
    override val exampleFileExtensions: Set<String> = setOf("json")
    override val contractFileExtensions: Set<String> = OPENAPI_FILE_EXTENSIONS.toSet()

    override fun contractFileToFeature(contractFile: File): Feature {
        return parseContractFileToFeature(contractFile)
    }

    override fun getScenarioDescription(scenario: Scenario): String {
        return scenario.testDescription().split("Scenario: ").last()
    }

    override fun getScenariosFromFeature(feature: Feature, extensive: Boolean): List<Scenario> {
        if (!extensive) {
            return feature.scenarios.filter { it.status in 200..299 }
        }

        return feature.scenarios
    }

    // GENERATION METHODS
    override fun generateExample(feature: Feature, scenario: Scenario, dictionary: Dictionary): Pair<String, String> {
        val request = scenario.generateHttpRequest()
        val requestHttpPathPattern = scenario.httpRequestPattern.httpPathPattern
        val updatedRequest = request.substituteDictionaryValues(dictionary, forceSubstitution = true, requestHttpPathPattern)

        val response = feature.lookupResponse(scenario).cleanup()
        val updatedResponse = response.substituteDictionaryValues(dictionary, forceSubstitution = true)

        val scenarioStub = ScenarioStub(updatedRequest, updatedResponse)
        val stubJSON = scenarioStub.toJSON().toStringLiteral()
        val uniqueName = uniqueNameForApiOperation(request, "", scenarioStub.response.status)

        return Pair("$uniqueName.json", stubJSON)
    }

    override fun getExistingExampleOrNull(scenario: Scenario, exampleFiles: List<File>): Pair<File, Result>? {
        val examples  = exampleFiles.toExamples()
        return examples.firstNotNullOfOrNull { example ->
            val response = example.response

            when (val matchResult = scenario.matchesMock(example.request, response)) {
                is Result.Success -> example.file to matchResult
                is Result.Failure -> {
                    val isFailureRelatedToScenario = matchResult.getFailureBreadCrumbs("").none { breadCrumb ->
                        breadCrumb.contains(PATH_BREAD_CRUMB)
                                || breadCrumb.contains(METHOD_BREAD_CRUMB)
                                || breadCrumb.contains("REQUEST.HEADERS.Content-Type")
                                || breadCrumb.contains("STATUS")
                    }
                    if (isFailureRelatedToScenario) example.file to matchResult else null
                }
            }
        }
    }

    private fun List<File>.toExamples(): List<ExampleFromFile> {
        return this.map { ExampleFromFile(it) }
    }

    private fun HttpResponse.cleanup(): HttpResponse {
        return this.copy(headers = this.headers.minus(SPECMATIC_RESULT_HEADER))
    }

    // VALIDATION METHODS
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