package run.qontract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Feature
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

        assertThat(Feature(generatedGherkin)).isEqualTo(Feature(expectedGherkin))
    }

    @Test
    fun `conversion with simple type bodies`() {
        val (wsdlContent, expectedGherkin) = readContracts("hello")

        val wsdl = WSDL(toXMLNode(wsdlContent))
        val generatedGherkin: String = wsdl.convertToGherkin().trim()

        assertThat(Feature(generatedGherkin)).isEqualTo(Feature(expectedGherkin))
    }

    private fun readContracts(filename: String): Pair<String, String> {
        val wsdlContent = readTextResource("wsdl/$filename.wsdl")
        val expectedGherkin = readTextResource("wsdl/$filename.qontract").trimIndent().trim()
        return Pair(wsdlContent, expectedGherkin)
    }

    private fun readTextResource(path: String) =
        File(
            javaClass.classLoader.getResource(path)?.file
                ?: throw ContractException("Could not find resource file $path")
        ).readText()
}
