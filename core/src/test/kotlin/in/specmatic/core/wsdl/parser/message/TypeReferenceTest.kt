package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.value.toXMLNode
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
        assertThat(wsdlElement).isInstanceOf(ReferredType::class.java)
        assertThat(typeName).isEqualTo("ns0_Person")
    }
}