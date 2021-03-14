package run.qontract.core.wsdl.parser.message

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL

internal class TypeReferenceTest {
    @Test
    fun `simple type node`() {
        val element = toXMLNode("<xsd:element type=\"xsd:string\" />").copy(namespaces = mapOf("xsd" to "http://www.w3.org/2001/XMLSchema"))
        val typeReference = TypeReference(element, mockk())
        val (typeName, wsdlElement) = typeReference.getWSDLElement()
        assertThat(wsdlElement).isInstanceOf(SimpleElement::class.java)
        assertThat(typeName).isEqualTo("xsd_string")
    }

    @Test
    fun `complex type node`() {
        val element = toXMLNode("<xsd:element type=\"ns0:Person\" />").copy(namespaces = mapOf("ns0" to "http://person-service"))
        val typeReference = TypeReference(element, mockk())
        val (typeName, wsdlElement) = typeReference.getWSDLElement()
        assertThat(wsdlElement).isInstanceOf(ComplexElement::class.java)
        assertThat(typeName).isEqualTo("ns0_Person")
    }
}