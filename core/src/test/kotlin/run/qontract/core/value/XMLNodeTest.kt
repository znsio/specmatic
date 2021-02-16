package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import run.qontract.core.pattern.ContractException

internal class XMLNodeTest {
    @Test
    fun `parse XML`() {
        val node = XMLNode("<data>data</data>")

        assertThat(node).isEqualTo(XMLNode("data", "data", emptyMap(), listOf(StringValue("data")), "", emptyMap()))
        assertThat(node.toStringValue()).isEqualTo("<data>data</data>")
    }

    @Test
    fun `parses the namespace prefix of a node`() {
        val node = XMLNode("<ns:data>data</ns:data>")

        assertThat(node.namespacePrefix).isEqualTo("ns")
    }

    @Test
    fun `parses the namespaces declared in a node`() {
        val node = XMLNode("<data xmlns:data=\"http://data\">data</data>")

        assertThat(node.namespaces).isEqualTo(mapOf("data" to "http://data"))
    }

    @Test
    fun `looks up a child node by name`() {
        val personDetailsXmlNode = XMLNode("<data><person/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByName("person")
        assertThat(personNode).isNotNull
    }

    @Test
    fun `returns null if there is no child node by that`() {
        val personDetailsXmlNode = XMLNode("<data><nobody/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByName("person")
        assertThat(personNode).isNull()
    }

    @Test
    fun `path lookup looks up a node by name`() {
        val personDetailsXmlNode = XMLNode("<data><person/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByPath("person")
        assertThat(personNode).isNotNull
    }

    @Test
    fun `path lookup returns null if there is no child node by the given name`() {
        val personDetailsXmlNode = XMLNode("<data><nobody/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByPath("person")
        assertThat(personNode).isNull()
    }

    @Test
    fun `looks up a node by path`() {
        val dataNode = XMLNode("<data><person><name>Jill</name></person></data>")

        val nameNode = dataNode.findFirstChildByPath("person.name")
        assertThat(nameNode).isNotNull
    }

    @Test
    fun `returns null if there is no node at the given path`() {
        val dataNode = XMLNode("<data><person><name>Jill</name></person></data>")

        val nameNode = dataNode.findFirstChildByPath("person.nobody")
        assertThat(nameNode).isNull()
    }

    @Test
    fun `looks up only the first node by path`() {
        val personDetailsXmlNode = XMLNode("<data><person><name>Jack</name></person><person><name>Jill</name></person></data>")

        val nameNode = personDetailsXmlNode.findFirstChildByPath("person.name")
        val name = nameNode?.nodes?.get(0)?.toStringValue()

        assertThat(name).isEqualTo("Jack")
    }

    @Test
    fun `resolves the namespace prefix of a node when provided in the same node and shows it in qname`() {
        val xmlNode = XMLNode("<ns:data xmlns:ns=\"http://example.com/namespace/url\" />")

        assertThat(xmlNode.qname).isEqualTo("{http://example.com/namespace/url}data")
    }

    @Test
    fun `qname uses xmlns attribute if provided`() {
        val xmlNode = XMLNode("<data xmlns=\"http://example.com/namespace/url\" />")

        assertThat(xmlNode.qname).isEqualTo("{http://example.com/namespace/url}data")
    }

    @Test
    fun `if no namespace exists, only the name is provided as qname`() {
        val xmlNode = XMLNode("<data/>")

        assertThat(xmlNode.qname).isEqualTo("data")
    }

    @Test
    fun `if a namespace prefix can't be resolved, an exception is thrown when reading qname`() {
        val xmlNode = XMLNode("<ns:data/>")

        assertThrows<ContractException> { xmlNode.qname }
    }

    @Test
    fun `an inherited namespace prefix gets resolved as the qname`() {
        val parentNode = XMLNode("<parent xmlns:ns=\"http://example.com/namespace/url\"><ns:child /></parent>")
        val childNode = parentNode.findFirstChildByName("child")

        println(parentNode.namespaces)
        println(childNode?.namespaces)

        assertThat(childNode?.qname).isEqualTo("{http://example.com/namespace/url}child")
    }
}
