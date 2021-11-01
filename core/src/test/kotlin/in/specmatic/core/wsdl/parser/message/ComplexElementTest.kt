package `in`.specmatic.core.wsdl.parser.message

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.TYPE_ATTRIBUTE_NAME
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.TYPE_NODE_NAME
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo

internal class ComplexElementTest {
    @Test
    fun `does not recurse`() {
        val element = toXMLNode("<xsd:element type=\"ns0:Person\"/>")

        val complexElement = ComplexElement("ns0:PersonRequest", element, mockk())
        val preExistingTypes = mapOf("Name" to XMLPattern("<name>(string)</name>"))
        val wsdlTypeInfo = complexElement.getGherkinTypes("PersonRequest", preExistingTypes, setOf("PersonRequest"))

        assertThat(wsdlTypeInfo).isEqualTo(WSDLTypeInfo(types = preExistingTypes))
    }

    @Test
    fun `returns types`() {
        val element = toXMLNode("<xsd:element type=\"ns0:Person\"/>").withPrimitiveNamespace()

        val wsdl = mockk<WSDL>()

        val complexType2 = mockk<ComplexType>()
        every {
            complexType2.generateChildren(any(), any(), any())
        } returns WSDLTypeInfo(listOf(toXMLNode("<data>(string)</data>")))
        every {
            wsdl.getComplexTypeNode(element)
        } returns complexType2

        every {
            wsdl.getQualification(element, "ns0:PersonRequest")
        } returns UnqualifiedNamespace("Person")

        every {
            wsdl.getWSDLElementType(any(), any())
        } returns InlineType("TypeName", toXMLNode("<element name=\"data\" type=\"xsd:string\" />").withPrimitiveNamespace(), wsdl)

        val complexElement = ComplexElement("ns0:PersonRequest", element, wsdl)
        val wsdlTypeInfo = complexElement.getGherkinTypes("PersonRequest", emptyMap(), emptySet())

        val expected = WSDLTypeInfo(listOf(toXMLNode("<Person $TYPE_ATTRIBUTE_NAME=\"PersonRequest\"/>")), mapOf("PersonRequest" to XMLPattern("<$TYPE_NODE_NAME><data>(string)</data></$TYPE_NODE_NAME>")))
        assertThat(wsdlTypeInfo).isEqualTo(expected)
    }

    @Test
    fun `complex node with no children returns no children`() {
        val typeInfo = ComplexElement("", mockk(), mockk()).generateChildren("", toXMLNode("<complexType/>"), emptyMap(), emptySet())
        assertThat(typeInfo.nodes).isEmpty()
    }
}

internal fun XMLNode.withPrimitiveNamespace(): XMLNode {
    return this.copy(namespaces = mapOf("xsd" to primitiveNamespace))
}
