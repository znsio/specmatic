package run.qontract.core.value

import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Node
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern2
import run.qontract.core.utilities.xmlToString
import javax.xml.parsers.DocumentBuilderFactory

fun XMLNode(document: Document): XMLNode {
    val node = document.documentElement
    return XMLNode(node) as XMLNode
}

fun XMLNode(node: Node): IXMLValue {
    return when (node.nodeType) {
        Node.TEXT_NODE -> StringValue(node.textContent)
        else -> XMLNode(node.nodeName, attributes(node), nodes(node))
    }
}

private fun nodes(node: Node): List<IXMLValue> {
    return 0.until(node.childNodes.length).map {
        node.childNodes.item(it)
    }.fold(listOf()) { acc, item ->
        acc.plus(XMLNode(item))
    }
}

private fun attributes(node: Node): Map<String, StringValue> {
    return 0.until(node.attributes.length).map {
        node.attributes.item(it) as Attr
    }.fold(mapOf()) { acc, item ->
        acc.plus(item.name to StringValue(item.value))
    }
}

data class XMLNode(val name: String, val attributes: Map<String, StringValue> = emptyMap(), val nodes: List<IXMLValue> = emptyList()) : IXMLValue, ListValue {
    override val httpContentType: String = "text/xml"

    override val list: List<Value>
        get() = nodes

    private fun build(): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        val document = builder.newDocument()

        val node = build(document)
        document.appendChild(node)

        return document
    }

    override fun build(document: Document): Node {
        val newElement = document.createElement(name)

        for(entry in attributes) {
            newElement.setAttribute(entry.key, entry.value.toStringValue())
        }

        val newNodes = nodes.map {
            it.build(document)
        }

        for(node in newNodes) {
            newElement.appendChild(node)
        }

        return newElement
    }

    override fun displayableValue(): String = toStringValue()

    override fun toStringValue(): String {
        return xmlToString(build())
    }

    override fun displayableType(): String = "xml"
    override fun exactMatchElseType(): XMLPattern2 {
        return XMLPattern2(this)
    }

    override fun type(): Pattern {
        return XMLPattern2()
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> {
        TODO("Not yet implemented")
    }

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> {
        TODO("Not yet implemented")
    }

    override fun listOf(valueList: List<Value>): Value {
        return XMLNode("", emptyMap(), valueList.map { it as XMLNode })
    }
}