package `in`.specmatic.conversions

import `in`.specmatic.core.ScenarioInfo
import io.cucumber.messages.Messages

interface IncludedSpecification {
    fun toScenarioInfos(): List<ScenarioInfo>
    fun matches(
        specmaticScenarioInfo: ScenarioInfo,
        steps: List<Messages.GherkinDocument.Feature.Step>
    ): List<ScenarioInfo>
}
