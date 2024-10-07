package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesGenerateBase
import application.exampleGeneration.ExamplesGenerateCommon
import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.*
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.mock.ScenarioStub
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "examples",
    description = ["Generate JSON Examples with Request and Response from an OpenApi Contract File"],
    subcommands = [OpenApiExamplesValidate::class, OpenApiExamplesInteractive::class]
)
class OpenApiExamplesGenerate: ExamplesGenerateBase<Feature, Scenario>(), OpenApiExamplesGenerateCommon {
    @Option(names = ["--extensive"], description = ["Generate all examples (by default, generates one example per 2xx API)"], defaultValue = "false")
    override var extensive: Boolean = false
}

interface OpenApiExamplesGenerateCommon: ExamplesGenerateCommon<Feature, Scenario>, OpenApiExamplesCommon {
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
}