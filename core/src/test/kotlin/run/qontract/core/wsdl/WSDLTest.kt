package run.qontract.core.wsdl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.CONTRACT_EXTENSION
import run.qontract.core.parseGherkinStringToFeature
import run.qontract.core.pattern.ContractException
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL
import java.io.File

class WSDLTest {
    @Test
    fun `conversion with bare types`() {
        val (wsdlContent, expectedGherkin) = readContracts("stockquote")

        val wsdl = WSDL(toXMLNode(wsdlContent))

        val generatedGherkin: String = wsdl.convertToGherkin().trim()

        val expectedFeature = parseGherkinStringToFeature(generatedGherkin)
        val generatedFeature = parseGherkinStringToFeature(expectedGherkin)

        assertThat(expectedFeature).isEqualTo(generatedFeature)
    }

    @Test
    fun `conversion with simple type bodies`() {
        val (wsdlContent, expectedGherkin) = readContracts("hello")

        val wsdl = WSDL(toXMLNode(wsdlContent))
        val generatedGherkin: String = wsdl.convertToGherkin().trim()

        assertThat(parseGherkinStringToFeature(generatedGherkin)).isEqualTo(parseGherkinStringToFeature(expectedGherkin))
    }

    private fun readContracts(filename: String): Pair<String, String> {
        val wsdlContent = readTextResource("wsdl/$filename.wsdl")
        val expectedGherkin = readTextResource("wsdl/$filename.$CONTRACT_EXTENSION").trimIndent().trim()
        return Pair(wsdlContent, expectedGherkin)
    }

    fun readTextResource(path: String) =
        File(
            javaClass.classLoader.getResource(path)?.file
                ?: throw ContractException("Could not find resource file $path")
        ).readText()
}
