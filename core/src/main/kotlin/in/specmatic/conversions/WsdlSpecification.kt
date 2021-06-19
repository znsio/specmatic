package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.WSDL
import io.cucumber.messages.types.FeatureChild
import io.cucumber.messages.types.Step
import io.swagger.v3.parser.util.ClasspathHelper
import org.apache.commons.io.FileUtils
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class WsdlSpecification(private val wsdlFile: String) : IncludedSpecification {

    override fun matches(
        specmaticScenarioInfo: ScenarioInfo,
        steps: List<Step>
    ): List<ScenarioInfo> {
        val openApiScenarioInfos = toScenarioInfos()
        if (openApiScenarioInfos.isNullOrEmpty() || !steps.isNotEmpty()) return listOf(specmaticScenarioInfo)
        val result: MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> =
            specmaticScenarioInfo to openApiScenarioInfos to
                    ::matchesRequest then
                    ::matchesResponse otherwise
                    ::handleError
        when (result) {
            is MatchFailure -> throw ContractException(result.error.message)
            is MatchSuccess -> return result.value.second
        }
    }

    fun matchesRequest(parameters: Pair<ScenarioInfo, List<ScenarioInfo>>): MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> {
        val (specmaticScenarioInfo, wsdlScenarioInfos) = parameters

        val matchingScenarioInfos = wsdlScenarioInfos.filter {
            it.httpRequestPattern.matches(
                specmaticScenarioInfo.httpRequestPattern.generate(
                    Resolver()
                ), Resolver()
            ).isTrue()
        }

        return when {
            matchingScenarioInfos.isEmpty() -> MatchFailure(
                Result.Failure(
                    """Scenario: "${specmaticScenarioInfo.scenarioName}" request is not as per included wsdl / OpenApi spec"""
                )
            )
            else -> MatchSuccess(specmaticScenarioInfo to matchingScenarioInfos)
        }
    }

    private fun matchesResponse(parameters: Pair<ScenarioInfo, List<ScenarioInfo>>): MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> {
        val (specmaticScenarioInfo, wsdlScenarioInfos) = parameters

        val matchingScenarioInfos = wsdlScenarioInfos.filter {
            it.httpResponsePattern.matches(
                specmaticScenarioInfo.httpResponsePattern.generateResponse(
                    Resolver()
                ), Resolver()
            ).isTrue()
        }

        return when {
            matchingScenarioInfos.isEmpty() -> MatchFailure(
                Result.Failure(
                    """Scenario: "${specmaticScenarioInfo.scenarioName}" response is not as per included wsdl / OpenApi spec"""
                )
            )
            else -> MatchSuccess(specmaticScenarioInfo to matchingScenarioInfos)
        }
    }

    override fun toScenarioInfos(): List<ScenarioInfo> {
        return scenarioInfos(wsdlToFeatureChildren(wsdlFile), "")
    }

    private fun wsdlToFeatureChildren(wsdlFile: String): List<FeatureChild> {
        val wsdlContent = readContentFromLocation(wsdlFile)
        val wsdl = WSDL(toXMLNode(wsdlContent!!), wsdlFile)
        val gherkin = wsdl.convertToGherkin().trim()
        return parseGherkinString(gherkin).feature.children
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