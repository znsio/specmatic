package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.ScenarioInfo
import io.cucumber.messages.types.Step

interface IncludedSpecification {
    fun toScenarioInfos(): Pair<List<ScenarioInfo>, Map<String, Pair<HttpRequest, HttpResponse>>>
    fun matches(
        specmaticScenarioInfo: ScenarioInfo,
        steps: List<Step>
    ): List<ScenarioInfo>
}
