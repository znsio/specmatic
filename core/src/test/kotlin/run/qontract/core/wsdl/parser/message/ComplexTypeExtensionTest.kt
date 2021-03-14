package run.qontract.core.wsdl.parser.message

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo

internal class ComplexTypeExtensionTest {
    @Test
    fun `complex type with extension`() {
        val child = toXMLNode("<xsd:complexContent><xsd:extension base=\"otherComplexType\"><xsd:sequence/></xsd:extension></xsd:complexContent>")

        val parent: ComplexElement = mockk()
        val parentComplexType = toXMLNode("<complexType/>")

        every {
            parent.generateChildren(any(), parentComplexType, any(), any())
        }.returns(WSDLTypeInfo(listOf(toXMLNode("<node1/>")), mapOf("Name" to XMLPattern("<name>(string)</name>"))))

        val childTypes = mapOf("Address" to XMLPattern("<address>(string)</address>"))
        every {
            parent.generateChildren(any(), toXMLNode("<xsd:sequence/>"), any(), any())
        }.returns(WSDLTypeInfo(listOf(toXMLNode("<node2/>")), childTypes))

        val wsdl = mockk<WSDL>()
        every {
            wsdl.findTypeFromAttribute(any(), "base")
        }.returns(parentComplexType)

        val wsdlTypeInfo = ComplexTypeExtension(parent, child, wsdl, "ParentType").process(WSDLTypeInfo(), emptyMap(), emptySet())

        val expected = WSDLTypeInfo(listOf(toXMLNode("<node1/>"), toXMLNode("<node2/>")), childTypes)
        assertThat(wsdlTypeInfo).isEqualTo(expected)
    }

    @Test
    fun `complex type which extends but actually does not actually specify any extensions`() {
        val child = toXMLNode("<xsd:complexContent><xsd:extension base=\"otherComplexType\"/></xsd:complexContent>")

        val parent: ComplexElement = mockk()
        val parentComplexType = toXMLNode("<complexType/>")

        val parentTypes = mapOf("Name" to XMLPattern("<name>(string)</name>"))
        every {
            parent.generateChildren(any(), parentComplexType, any(), any())
        }.returns(WSDLTypeInfo(listOf(toXMLNode("<node1/>")), parentTypes))

        val wsdl = mockk<WSDL>()
        every {
            wsdl.findTypeFromAttribute(any(), "base")
        }.returns(parentComplexType)

        val wsdlTypeInfo = ComplexTypeExtension(parent, child, wsdl, "ParentType").process(WSDLTypeInfo(), emptyMap(), emptySet())

        val expected = WSDLTypeInfo(listOf(toXMLNode("<node1/>")), parentTypes)
        assertThat(wsdlTypeInfo).isEqualTo(expected)
    }
}