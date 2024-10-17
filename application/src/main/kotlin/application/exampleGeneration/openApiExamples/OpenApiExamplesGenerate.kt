package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesGenerationStrategy
import application.exampleGeneration.ExamplesGenerateBase
import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.*
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.mock.ScenarioStub
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "examples", mixinStandardHelpOptions = true,
    description = ["Generate Externalised Examples from an OpenApi Contract"],
    subcommands = [OpenApiExamplesValidate::class, OpenApiExamplesInteractive::class]
)
class OpenApiExamplesGenerate: ExamplesGenerateBase<Feature, Scenario> (
    featureStrategy = OpenApiExamplesFeatureStrategy(), generationStrategy = OpenApiExamplesGenerationStrategy()
) {
    @Option(names = ["--extensive"], description = ["Generate all examples (by default, generates one example per 2xx API)"], defaultValue = "false")
    override var extensive: Boolean = false
}

class OpenApiExamplesGenerationStrategy: ExamplesGenerationStrategy<Feature, Scenario> {
    override fun generateExample(feature: Feature, scenario: Scenario): Pair<String, String> {
        val request = scenario.generateHttpRequest()
        val response = feature.lookupResponse(scenario).cleanup()

        val scenarioStub = ScenarioStub(request, response)
        val stubJSON = scenarioStub.toJSON().toStringLiteral()
        val uniqueName = uniqueNameForApiOperation(request, "", scenarioStub.response.status)

        return Pair("$uniqueName.json", stubJSON)
    }

    override fun getExistingExamples(scenario: Scenario, exampleFiles: List<File>): List<Pair<File, Result>> {
        val examples  = exampleFiles.toExamples()
        return examples.mapNotNull { example ->
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