package `in`.specmatic.conversions

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.ScenarioInfo
import `in`.specmatic.core.pattern.ContractException
import io.cucumber.messages.Messages

interface IncludedSpecification {
    fun validateCompliance(scenarioInfo: ScenarioInfo, steps: List<Messages.GherkinDocument.Feature.Step>)
    fun toScenarioInfos(): List<ScenarioInfo>

    fun validateScenarioInfoCompliance(
        wsdlScenarioInfos: List<ScenarioInfo>,
        steps: List<Messages.GherkinDocument.Feature.Step>,
        scenarioInfo: ScenarioInfo
    ) {
        if (!wsdlScenarioInfos.isNullOrEmpty() && steps.isNotEmpty()) {
            if (!wsdlScenarioInfos!!.any {
                    it.httpRequestPattern.matches(
                        scenarioInfo.httpRequestPattern.generate(
                            Resolver()
                        ), Resolver()
                    ).isTrue()
                }) {
                throw ContractException("""Scenario: "${scenarioInfo.scenarioName}" request is not as per included wsdl / OpenApi spec""")
            }
            if (!wsdlScenarioInfos!!.any {
                    it.httpResponsePattern.matches(
                        scenarioInfo.httpResponsePattern.generateResponse(
                            Resolver()
                        ), Resolver()
                    ).isTrue()
                }) {
                throw ContractException("""Scenario: "${scenarioInfo.scenarioName}" response is not as per included wsdl / OpenApi spec""")
            }

        }
    }
}
