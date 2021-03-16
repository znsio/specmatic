package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import run.qontract.core.pattern.ContractException

internal class XMLNodeTest {
    @Test
    fun `parse XML`() {
        val node = toXMLNode("<data>data</data>")

        assertThat(node).isEqualTo(XMLNode("data", "data", emptyMap(), listOf(StringValue("data")), "", emptyMap()))
        assertThat(node.toStringValue()).isEqualTo("<data>data</data>")
    }

    @Test
    fun `parses the namespace prefix of a node`() {
        val node = toXMLNode("<ns:data>data</ns:data>")

        assertThat(node.namespacePrefix).isEqualTo("ns")
    }

    @Test
    fun `parses the namespaces declared in a node`() {
        val node = toXMLNode("<data xmlns:data=\"http://data\">data</data>")

        assertThat(node.namespaces).isEqualTo(mapOf("data" to "http://data"))
    }

    @Test
    fun `looks up a child node by name`() {
        val personDetailsXmlNode = toXMLNode("<data><person/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByName("person")
        assertThat(personNode).isNotNull
    }

    @Test
    fun `returns null if there is no child node by that name`() {
        val personDetailsXmlNode = toXMLNode("<data><nobody/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByName("person")
        assertThat(personNode).isNull()
    }

    @Test
    fun `path lookup looks up a child node by name`() {
        val personDetailsXmlNode = toXMLNode("<data><person/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByPath("person")
        assertThat(personNode).isNotNull
    }

    @Test
    fun `path lookup returns null if there is no child node by the given name`() {
        val personDetailsXmlNode = toXMLNode("<data><nobody/></data>")

        val personNode = personDetailsXmlNode.findFirstChildByPath("person")
        assertThat(personNode).isNull()
    }

    @Test
    fun `looks up a node by path`() {
        val dataNode = toXMLNode("<data><person><name>Jill</name></person></data>")

        val nameNode = dataNode.findFirstChildByPath("person.name")
        assertThat(nameNode).isNotNull
    }

    @Test
    fun `returns null if there is no node at the given path`() {
        val dataNode = toXMLNode("<data><person><name>Jill</name></person></data>")

        val nameNode = dataNode.findFirstChildByPath("person.nobody")
        assertThat(nameNode).isNull()
    }

    @Test
    fun `looks up only the first node by path`() {
        val personDetailsXmlNode = toXMLNode("<data><person><name>Jack</name></person><person><name>Jill</name></person></data>")

        val nameNode = personDetailsXmlNode.findFirstChildByPath("person.name")
        val name = nameNode?.childNodes?.get(0)?.toStringValue()

        assertThat(name).isEqualTo("Jack")
    }

    @Test
    fun `resolves the namespace prefix of a node when provided in the same node and shows it in qname`() {
        val xmlNode = toXMLNode("<ns:data xmlns:ns=\"http://example.com/namespace/url\" />")

        assertThat(xmlNode.qname).isEqualTo("{http://example.com/namespace/url}data")
    }

    @Test
    fun `qname uses xmlns attribute if provided`() {
        val xmlNode = toXMLNode("<data xmlns=\"http://example.com/namespace/url\" />")

        assertThat(xmlNode.qname).isEqualTo("{http://example.com/namespace/url}data")
    }

    @Test
    fun `if no namespace exists, only the name is provided as qname`() {
        val xmlNode = toXMLNode("<data/>")

        assertThat(xmlNode.qname).isEqualTo("data")
    }

    @Test
    fun `if a namespace prefix can't be resolved, an exception is thrown when reading qname`() {
        val xmlNode = toXMLNode("<ns:data/>")

        assertThrows<ContractException> { xmlNode.qname }
    }

    @Test
    fun `an inherited namespace prefix gets resolved as the qname`() {
        val parentNode = toXMLNode("<parent xmlns:ns=\"http://example.com/namespace/url\"><ns:child /></parent>")
        val childNode = parentNode.findFirstChildByName("child")

        assertThat(childNode?.qname).isEqualTo("{http://example.com/namespace/url}child")
    }

    @Test
    fun `an XML node can create another nodes that inherits all its namespace`() {
        val initialNamespace = "http://data"
        val initialNode = toXMLNode("<data xmlns:data=\"$initialNamespace\">data</data>")
        val createdFromInitialNode = initialNode.createNewNode("newNode")

        assertThat(createdFromInitialNode.namespaces["data"]).isEqualTo(initialNamespace)
    }

    @Test
    fun `get namespace prefix from a name`() {
        assertThat("ns:name".namespacePrefix()).isEqualTo("ns")
    }

    @Test
    fun `get namespace prefix from a name with no prefix returns a blank string`() {
        assertThat("name".namespacePrefix()).isBlank
    }

    @Test
    fun `strip out the namespace prefix from a name`() {
        assertThat("ns:name".withoutNamespacePrefix()).isEqualTo("name")
    }

    @Test
    fun `stripping out the namespace prefix from a name with no prefix returns the name`() {
        assertThat("name".withoutNamespacePrefix()).isEqualTo("name")
    }

    @Test
    fun `resolves the namespace of a name in terms of known namespaces`() {
        val node = toXMLNode("<data xmlns:data=\"http://data\">data</data>")
        assertThat(node.resolveNamespace("data:stuff")).isEqualTo("http://data")
    }

    @Test
    fun `find first child by name`() {
        val node = toXMLNode("<person><name/></person>")
        assertThat(node.findFirstChildByName("name")).isInstanceOf(XMLNode::class.java)
    }

    @Test
    fun `return null if there is no child by the given name`() {
        val node = toXMLNode("<person><name/></person>")
        assertThat(node.findFirstChildByName("firstname")).isNull()
    }

    @Test
    fun `find first child by path`() {
        val node = toXMLNode("<person><name><firstname/></name></person>")
        assertThat(node.findFirstChildByPath("name.firstname")).isInstanceOf(XMLNode::class.java)
    }

    @Test
    fun `return null if there is no node at the given path`() {
        val node = toXMLNode("<person><name><firstname/></name></person>")
        assertThat(node.findFirstChildByPath("name.lastname")).isNull()
    }

    @Test
    fun `should find all child nodes by the given name`() {
        val node = toXMLNode("<person><name/><address/><address/></person>")
        assertThat(node.findChildrenByName("address")).hasSize(2)
    }

    @Test
    fun `should return an empty list if there is no child node by the given name`() {
        val node = toXMLNode("<person><name/></person>")
        assertThat(node.findChildrenByName("address")).isEmpty()
    }

    @Test
    fun `should return the namespace of the prefix in the given name`() {
        val node = toXMLNode("<person xmlns:ns0=\"http://ns\"><name/></person>")
        assertThat(node.resolveNamespace("ns0:address")).isEqualTo("http://ns")
    }

    @Test
    fun `should return an empty string if the given name has no prefix`() {
        val node = toXMLNode("<person xmlns:ns0=\"http://ns\"><name/></person>")
        assertThat(node.resolveNamespace("address")).isBlank
    }

    @Test
    fun `should throw an exception if the prefix in the given name is not recognised`() {
        val node = toXMLNode("<person xmlns:ns0=\"http://ns\"><name/></person>")
        assertThrows<ContractException> { node.resolveNamespace("ns1:address") }
    }

    @Test
    fun `namespace definitions are inherited 3 levels down`() {
        val expectedNamespaces = mapOf("ns1" to "http://one", "ns2" to "http://two", "ns3" to "http://three")

        val node = toXMLNode("""<level1 xmlns:ns1="http://one"><level2 xmlns:ns2="http://two"><level3 xmlns:ns3="http://three" /></level2></level1>""")
        val level3 = node.findFirstChildByPath("level2.level3")!!

        expectedNamespaces.forEach { (prefix, namespace) ->
            assertThat(level3.namespaces[prefix]).isEqualTo(namespace)
        }
    }

    @Test
    fun `serialize to pretty string value`() {
        val originalXml = toXMLNode("<customer><firstname>Jill</firstname><surname>Granger</surname></customer>")

        val prettyStringValue = originalXml.toPrettyStringValue()
        print(prettyStringValue)
        assertThat(prettyStringValue).isEqualTo("""<customer>
  <firstname>Jill</firstname>
  <surname>Granger</surname>
</customer>""")
    }
}
