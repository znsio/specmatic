package run.qontract.core.wsdl.parser.message

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.ContractException
import run.qontract.core.value.toXMLNode

internal class InlineTypeTest {
    @Test
    fun `throws exception if there is no name`() {
        val type = InlineType("ParentType", toXMLNode("<xsd:element/>"), mockk())
        assertThatThrownBy { type.getWSDLElement() }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `simple element type`() {
        val element = toXMLNode("<xsd:element name=\"Name\" type=\"xsd:string\" />").copy(namespaces = mapOf("xsd" to "http://www.w3.org/2001/XMLSchema"))
        val type = InlineType("ParentType", element, mockk())
        val (typeName, _) = type.getWSDLElement()
        assertThat(typeName).isEqualTo("ParentType_Name")
    }

    @Test
    fun `complex element type`() {
        val element = toXMLNode("<xsd:element name=\"Customer\" type=\"ns0:Person\" />").copy(namespaces = mapOf("ns0" to "http://person-service"))
        val type = InlineType("ParentType", element, mockk())
        val (typeName, _) = type.getWSDLElement()
        assertThat(typeName).isEqualTo("ParentType_Customer")
    }
}