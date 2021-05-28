package `in`.specmatic.conversions

import `in`.specmatic.core.ScenarioInfo
import `in`.specmatic.core.parseGherkinString
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
        validateScenarioInfoCompliance(toScenarioInfos(), steps, scenarioInfo)
    }

    override fun toScenarioInfos(): List<ScenarioInfo> {
        return scenarioInfos(wsdlToFeatureChildren(wsdlFile), "")
    }

    private fun wsdlToFeatureChildren(wsdlFile: String): List<Messages.GherkinDocument.Feature.FeatureChild> {
        val wsdlContent = readContentFromLocation(wsdlFile)
        val wsdl = WSDL(toXMLNode(wsdlContent!!))
        val gherkin = wsdl.convertToGherkin().trim()
        return parseGherkinString(gherkin).feature.childrenList
    }

    private fun readContentFromLocation(location: String): String? {
        val adjustedLocation = location.replace("\\\\".toRegex(), "/")
        val fileScheme = "file:"
        val path = if (adjustedLocation.toLowerCase()
                .startsWith(fileScheme)
        ) Paths.get(URI.create(adjustedLocation)) else Paths.get(adjustedLocation)
        if (Files.exists(path)) {
            return FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8.displayName())
        } else {
            return ClasspathHelper.loadFileFromClasspath(adjustedLocation)
        }
    }
}