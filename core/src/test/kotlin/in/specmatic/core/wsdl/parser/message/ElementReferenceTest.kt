package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.value.FullyQualifiedName
import `in`.specmatic.core.value.StringValue
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.WSDL

internal class ElementReferenceTest {
    @Test
    fun `get a reference to another node`() {
        val refValue = "ns0:Person"
        val element = toXMLNode("<xsd:element xmlns:ns0=\"http://localhost/ns0\" ref=\"$refValue\" />")
        val wsdl: WSDL = mockk()

        val expectedResolvedElement: WSDLElement = mockk()
        val expectedTypeName = "ns0_Person"
        every {
            wsdl.getSOAPElement(ofType<FullyQualifiedName>(), null, mapOf("xmlns:ns0" to StringValue("http://localhost/ns0")))
        } returns expectedResolvedElement

        val reference = ElementReference(element, wsdl)
        val (typeName, resolvedElement) = reference.getWSDLElement()
        assertThat(typeName).isEqualTo(expectedTypeName)
        assertThat(resolvedElement).isEqualTo(expectedResolvedElement)
    }
}