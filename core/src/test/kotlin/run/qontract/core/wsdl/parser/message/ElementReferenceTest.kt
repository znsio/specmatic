package run.qontract.core.wsdl.parser.message

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL

internal class ElementReferenceTest {
    @Test
    fun `get a reference to another node`() {
        val refValue = "ns0:Person"
        val element = toXMLNode("<xsd:element ref=\"$refValue\" />")
        val wsdl: WSDL = mockk()

        val expectedResolvedElement: WSDLElement = mockk()
        val expectedTypeName = "ns0_Person"
        every {
            wsdl.getSOAPElement(refValue)
        } returns expectedResolvedElement

        val reference = ElementReference(element, wsdl)
        val (typeName, resolvedElement) = reference.getWSDLElement()
        assertThat(typeName).isEqualTo(expectedTypeName)
        assertThat(resolvedElement).isEqualTo(expectedResolvedElement)
    }
}