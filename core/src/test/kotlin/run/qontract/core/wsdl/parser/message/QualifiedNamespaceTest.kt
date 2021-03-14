package run.qontract.core.wsdl.parser.message

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL

internal class QualifiedNamespaceTest {
    @Test
    fun `gets the node name for a qualified element`() {
        val qualification = QualifiedNamespace(toXMLNode("<xsd:element name=\"ns:Customer\"/>"), "ns0:Person", mockk())
        assertThat(qualification.nodeName).isEqualTo("ns0:Customer")
    }

    @Test
    fun `a qualified element with no namespace in the wsdl type reference gets no namespace`() {
        val qualification = QualifiedNamespace(toXMLNode("<xsd:element name=\"ns:Customer\"/>"), "Person", mockk())
        assertThat(qualification.namespacePrefix).isEqualTo(emptyList<String>())
    }

    @Test
    fun `a qualified element with a namespace in the wsdl type reference`() {
        val wsdl: WSDL = mockk()

        val element = toXMLNode("<xsd:element name=\"ns:Customer\"/>")
        every {
            wsdl.mapToNamespacePrefixInDefinitions("ns0", element)
        }.returns("ns1")

        val qualification = QualifiedNamespace(element, "ns0:Person", wsdl)
        assertThat(qualification.namespacePrefix).isEqualTo(listOf("ns1"))
    }
}