package run.qontract.core.wsdl.parser.message

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo

internal class ElementInComplexTypeTest {
    @Test
    fun `process an element child in a complex type`() {
        val xmlElement = toXMLNode("<xsd:element name=\"Name\" type=\"ns0:Name\" />").copy(namespaces = mapOf("ns0" to "http://name-service"))
        val parentTypeName = "ParentType"

        val complexNameElement = mockk<WSDLElement>()
        val data2Type = mapOf("Data2" to XMLPattern(toXMLNode("<dataB/>")))
        val returned =
            WSDLTypeInfo(listOf(toXMLNode("<node2/>")), data2Type, setOf("ns1"))
        every {
            complexNameElement.getQontractTypes("Name", emptyMap(), emptySet())
        } returns returned

        val childElementType = mockk<ChildElementType>()
        every {
            childElementType.getWSDLElement()
        } returns Pair("Name", complexNameElement)

        val wsdl = mockk<WSDL>()
        every {
            wsdl.getWSDLElementType(parentTypeName, xmlElement)
        } returns childElementType

        val elementInType = ElementInComplexType(xmlElement, wsdl, parentTypeName)

        val data1Type = mapOf("Data1" to XMLPattern(toXMLNode("<dataA/>")))
        val initial =
            WSDLTypeInfo(listOf(toXMLNode("<node1/>")), data1Type, setOf("ns0"))
        val wsdlTypeInfo = elementInType.process(initial, emptyMap(), emptySet())

        println(wsdlTypeInfo)

        val expected = WSDLTypeInfo(listOf(toXMLNode("<node1/>"), toXMLNode("<node2/>")), data1Type.plus(data2Type), setOf("ns1"))

        assertThat(wsdlTypeInfo).isEqualTo(expected)
    }

}