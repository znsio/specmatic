package io.specmatic.core.wsdl

import io.specmatic.Utils.readTextResource
import io.specmatic.core.CONTRACT_EXTENSION
import io.specmatic.core.parseGherkinStringToFeature
import io.specmatic.core.value.toXMLNode
import io.specmatic.core.wsdl.parser.WSDL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WSDLTest {
    @Test
    fun `conversion with bare types`() {
        val (wsdlContent, expectedGherkin) = readContracts("stockquote")

        val wsdl = WSDL(toXMLNode(wsdlContent), "")
        val gherkinFromWSDL: String = wsdl.convertToGherkin().trim()
        val featureFromWSDL = parseGherkinStringToFeature(gherkinFromWSDL)

        val featureFromExpectedGherkin = parseGherkinStringToFeature(expectedGherkin)

        assertThat(featureFromWSDL).isEqualTo(featureFromExpectedGherkin)
    }

    @Test
    fun `conversion with simple type bodies`() {
        val (wsdlContent, expectedGherkin) = readContracts("hello")

        val wsdl = WSDL(toXMLNode(wsdlContent), "")
        val generatedGherkin: String = wsdl.convertToGherkin().trim()

        assertThat(parseGherkinStringToFeature(generatedGherkin)).isEqualTo(parseGherkinStringToFeature(expectedGherkin))
    }

    private fun readContracts(filename: String): Pair<String, String> {
        val wsdlContent = readTextResource("wsdl/$filename.wsdl")
        val expectedGherkin = readTextResource("wsdl/$filename.$CONTRACT_EXTENSION").trimIndent().trim()
        return Pair(wsdlContent, expectedGherkin)
    }
}
