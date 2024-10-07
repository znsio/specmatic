package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesCommon
import io.specmatic.core.*
import java.io.File

interface OpenApiExamplesCommon: ExamplesCommon<Feature, Scenario> {
    override val exampleFileExtensions: Set<String> get() = setOf(JSON)
    override val contractFileExtensions: Set<String> get() = OPENAPI_FILE_EXTENSIONS.toSet()

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
}