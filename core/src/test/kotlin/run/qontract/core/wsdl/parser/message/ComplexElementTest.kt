package run.qontract.core.wsdl.parser.message

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo

internal class ComplexElementTest {
    @Test
    fun `does not recurse`() {
        val element = toXMLNode("<xsd:element type=\"ns0:Person\"/>")

        val complexElement = ComplexElement("ns0:PersonRequest", element, mockk())
        val preExistingTypes = mapOf("Name" to XMLPattern("<name>(string)</name>"))
        val wsdlTypeInfo = complexElement.getQontractTypes("PersonRequest", preExistingTypes, setOf("PersonRequest"))

        assertThat(wsdlTypeInfo).isEqualTo(WSDLTypeInfo(types = preExistingTypes))
    }

    @Test
    fun `returns types`() {
        val element = toXMLNode("<xsd:element type=\"ns0:Person\"/>").withPrimitiveNamespace()

        val wsdl = mockk<WSDL>()

        val complexType =
            toXMLNode("<complexType name=\"ns0:Person\"><annotation>docs</annotation><sequence><element name=\"data\" type=\"xsd:string\" /></sequence></complexType>").withPrimitiveNamespace()

        every {
            wsdl.getComplexTypeNode(element)
        } returns complexType

        every {
            wsdl.getQualification(toXMLNode("<xsd:element type=\"ns0:Person\"/>").withPrimitiveNamespace(), "ns0:PersonRequest")
        } returns UnqualifiedNamespace("Person")

        every {
            wsdl.getWSDLElementType(any(), any())
        } returns InlineType("TypeName", toXMLNode("<element name=\"data\" type=\"xsd:string\" />").withPrimitiveNamespace(), wsdl)

        val complexElement = ComplexElement("ns0:PersonRequest", element, wsdl)
        val wsdlTypeInfo = complexElement.getQontractTypes("PersonRequest", emptyMap(), emptySet())

        val expected = WSDLTypeInfo(listOf(toXMLNode("<Person qontract_type=\"PersonRequest\"/>")), mapOf("PersonRequest" to XMLPattern("<QONTRACT_TYPE><data>(string)</data></QONTRACT_TYPE>")))
        assertThat(wsdlTypeInfo).isEqualTo(expected)
    }
}

private fun XMLNode.withPrimitiveNamespace(): XMLNode {
    return this.copy(namespaces = mapOf("xsd" to primitiveNamespace))
}
