package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.WSDL
import io.cucumber.messages.types.FeatureChild
import io.cucumber.messages.types.Step
import io.swagger.v3.parser.util.ClasspathHelper
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

interface WSDLContent {
    val path: String

    fun read(): String?
}

class WSDLFile(private val location: String) : WSDLContent {
    override val path: String
        get() = location

    override fun read(): String? {
        val adjustedLocation = location.replace("\\\\".toRegex(), "/")
        val fileScheme = "file:"
        val path = if (adjustedLocation.lowercase()
                .startsWith(fileScheme)
        ) Paths.get(URI.create(adjustedLocation)) else Paths.get(adjustedLocation)
        return if (Files.exists(path)) {
            path.toFile().readText()
        } else {
            ClasspathHelper.loadFileFromClasspath(adjustedLocation)
        }
    }
}

class WsdlSpecification(private val wsdlFile: WSDLContent) : IncludedSpecification {
    private val openApiScenarioInfos = toScenarioInfos()

    override fun matches(
        specmaticScenarioInfo: ScenarioInfo,
        steps: List<Step>
    ): List<ScenarioInfo> {
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

    private fun wsdlToFeatureChildren(wsdlFile: WSDLContent): List<FeatureChild> {
        val wsdlContent = wsdlFile.read() ?: throw ContractException("Could not read WSDL file $wsdlFile")
        val wsdl = WSDL(toXMLNode(wsdlContent), wsdlFile.path)
        val gherkin = wsdl.convertToGherkin().trim()
        val feature = parseGherkinString(gherkin, wsdlFile.path).feature
        return feature.children
    }

}

fun wsdlContentToFeature(
    wsdlContent: String,
    path: String
): Feature {
    val wsdl = WSDL(toXMLNode(wsdlContent), path)
    val gherkin = wsdl.convertToGherkin().trim()
    return parseGherkinStringToFeature(gherkin, path)
}
