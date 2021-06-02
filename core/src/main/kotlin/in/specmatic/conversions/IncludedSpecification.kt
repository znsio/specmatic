package `in`.specmatic.conversions

import `in`.specmatic.core.ScenarioInfo
import io.cucumber.messages.Messages

interface IncludedSpecification {
    fun validateCompliance(scenarioInfo: ScenarioInfo, steps: List<Messages.GherkinDocument.Feature.Step>)
    fun identifyMatchingScenarioInfo(
        parsedScenarioInfo: ScenarioInfo,
        steps: List<Messages.GherkinDocument.Feature.Step>
    ): List<ScenarioInfo>

    fun toScenarioInfos(): List<ScenarioInfo>
}
