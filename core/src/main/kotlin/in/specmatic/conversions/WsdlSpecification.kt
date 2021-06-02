package `in`.specmatic.conversions

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.ScenarioInfo
import `in`.specmatic.core.parseGherkinString
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.scenarioInfos
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.WSDL
import io.cucumber.messages.Messages
import io.swagger.v3.parser.util.ClasspathHelper
import org.apache.commons.io.FileUtils
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class WsdlSpecification(private val wsdlFile: String) : IncludedSpecification {
    override fun validateCompliance(scenarioInfo: ScenarioInfo, steps: List<Messages.GherkinDocument.Feature.Step>) {
        val wsdlScenarioInfos = toScenarioInfos()
        if (!wsdlScenarioInfos.isNullOrEmpty() && steps.isNotEmpty()) {
            if (!wsdlScenarioInfos.any {
                    it.httpRequestPattern.matches(
                        scenarioInfo.httpRequestPattern.generate(
                            Resolver()
                        ), Resolver()
                    ).isTrue()
                }) {
                throw ContractException("""Scenario: "${scenarioInfo.scenarioName}" request is not as per included wsdl / OpenApi spec""")
            }
            if (!wsdlScenarioInfos.any {
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

    override fun identifyMatchingScenarioInfo(
        scenarioInfo: ScenarioInfo,
        steps: List<Messages.GherkinDocument.Feature.Step>
    ): List<ScenarioInfo> {
        return listOf(scenarioInfo)
    }

    override fun toScenarioInfos(): List<ScenarioInfo> {
        return scenarioInfos(wsdlToFeatureChildren(wsdlFile), "")
    }

    private fun wsdlToFeatureChildren(wsdlFile: String): List<Messages.GherkinDocument.Feature.FeatureChild> {
        val wsdlContent = readContentFromLocation(wsdlFile)
        val wsdl = WSDL(toXMLNode(wsdlContent!!), wsdlFile)
        val gherkin = wsdl.convertToGherkin().trim()
        return parseGherkinString(gherkin).feature.childrenList
    }

    private fun readContentFromLocation(location: String): String? {
        val adjustedLocation = location.replace("\\\\".toRegex(), "/")
        val fileScheme = "file:"
        val path = if (adjustedLocation.lowercase()
                .startsWith(fileScheme)
        ) Paths.get(URI.create(adjustedLocation)) else Paths.get(adjustedLocation)
        return if (Files.exists(path)) {
            FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8.displayName())
        } else {
            ClasspathHelper.loadFileFromClasspath(adjustedLocation)
        }
    }
}