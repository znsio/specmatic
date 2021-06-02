package `in`.specmatic.conversions

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.ScenarioInfo
import `in`.specmatic.core.pattern.ContractException
import io.cucumber.messages.Messages

interface IncludedSpecification {
    fun validateCompliance(scenarioInfo: ScenarioInfo, steps: List<Messages.GherkinDocument.Feature.Step>)
    fun identifyMatchingScenarioInfo(
        parsedScenarioInfo: ScenarioInfo,
        steps: List<Messages.GherkinDocument.Feature.Step>
    ): List<ScenarioInfo>

    fun toScenarioInfos(): List<ScenarioInfo>

    fun identifyMatchingScenarioInfos(
        wsdlScenarioInfos: List<ScenarioInfo>,
        steps: List<Messages.GherkinDocument.Feature.Step>,
        scenarioInfo: ScenarioInfo
    ): List<ScenarioInfo> {
        if (!wsdlScenarioInfos.isNullOrEmpty() && steps.isNotEmpty()) {
            return wsdlScenarioInfos.filter {
                it.httpRequestPattern.urlMatcher!!.matches(
                    scenarioInfo.httpRequestPattern.generate(Resolver()), Resolver()
                ).isTrue() &&
                        it.httpRequestPattern.method == scenarioInfo.httpRequestPattern.method &&
                        it.httpResponsePattern.status == scenarioInfo.httpResponsePattern.status
            }
        } else return listOf()
    }

    fun validateScenarioInfoCompliance(
        wsdlScenarioInfos: List<ScenarioInfo>,
        steps: List<Messages.GherkinDocument.Feature.Step>,
        scenarioInfo: ScenarioInfo
    ) {
        if (!wsdlScenarioInfos.isNullOrEmpty() && steps.isNotEmpty()) {
            val scenariosWithMatchingPath = wsdlScenarioInfos.filter {
                it.httpRequestPattern.urlMatcher!!.matches(
                    scenarioInfo.httpRequestPattern.generate(
                        Resolver()
                    ), Resolver()
                ).isTrue()
            }
            if (scenariosWithMatchingPath.isEmpty()) {
                throw ContractException(
                    """Scenario: "${scenarioInfo.scenarioName}" PATH: "${
                        scenarioInfo.httpRequestPattern.urlMatcher!!.generatePath(
                            Resolver()
                        )
                    }" is not as per included wsdl / OpenApi spec"""
                )
            }
            val scenariosWithMatchingPathAndMethod = scenariosWithMatchingPath.filter {
                it.httpRequestPattern.method == scenarioInfo.httpRequestPattern.method
            }
            if (scenariosWithMatchingPathAndMethod.isEmpty()) {
                throw ContractException(
                    """Scenario: "${scenarioInfo.scenarioName}" METHOD: "${
                        scenarioInfo.httpRequestPattern.method
                    }" is not as per included wsdl / OpenApi spec"""
                )
            }
            val scenarioWithMatchingPathMethodAndStatus = scenariosWithMatchingPathAndMethod.filter {
                it.httpResponsePattern.status == scenarioInfo.httpResponsePattern.status
            }
            if (scenarioWithMatchingPathMethodAndStatus.isEmpty()) {
                throw ContractException(
                    """Scenario: "${scenarioInfo.scenarioName}" RESPONSE STATUS: "${
                        scenarioInfo.httpResponsePattern.status
                    }" is not as per included wsdl / OpenApi spec"""
                )
            }
        }
    }

}
