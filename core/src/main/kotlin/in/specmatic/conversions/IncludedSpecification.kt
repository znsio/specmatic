package `in`.specmatic.conversions

import `in`.specmatic.core.ScenarioInfo
import io.cucumber.messages.types.Step

interface IncludedSpecification {
    fun toScenarioInfos(): List<ScenarioInfo>
    fun matches(
        specmaticScenarioInfo: ScenarioInfo,
        steps: List<Step>
    ): List<ScenarioInfo>
}
