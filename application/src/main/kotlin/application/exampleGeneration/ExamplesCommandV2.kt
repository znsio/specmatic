package application.exampleGeneration

import io.specmatic.core.*
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.mock.ScenarioStub
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.io.File

@Component
@CommandLine.Command(
    name = "examples-v2",
    mixinStandardHelpOptions = true,
    description = ["Generate examples from a OpenAPI contract file"]
)
class ExamplesCommandV2: ExamplesBaseCommand<Feature, Scenario>() {
    override fun contractFileToFeature(contractFile: File): Feature {
        return parseContractFileToFeature(contractFile)
    }

    override fun getScenariosFromFeature(feature: Feature): List<Scenario> {
        return feature.scenarios
    }

    override fun getScenarioDescription(feature: Feature, scenario: Scenario): String {
        return scenario.testDescription().split("Scenario: ").last()
    }

    override fun generateExampleFromScenario(feature: Feature, scenario: Scenario): Pair<String, String> {
        val request = scenario.generateHttpRequest()
        val response = feature.lookupResponse(request).cleanup()

        val scenarioStub = ScenarioStub(request, response)
        val stubJSON = scenarioStub.toJSON().toStringLiteral()
        val uniqueName = uniqueNameForApiOperation(request, "", response.status)

        return Pair(uniqueName, stubJSON)
    }

    private fun HttpResponse.cleanup(): HttpResponse {
        return this.copy(headers = this.headers.minus(SPECMATIC_RESULT_HEADER))
    }
}