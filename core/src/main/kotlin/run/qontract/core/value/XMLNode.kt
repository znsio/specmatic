package run.qontract.core.value

import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Node
import run.qontract.core.ExampleDeclarations
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.utilities.parseXML
import run.qontract.core.utilities.xmlToString
import javax.xml.parsers.DocumentBuilderFactory

fun XMLNode(document: Document): XMLNode {
    val node = document.documentElement
    return XMLNode(node) as XMLNode
}

fun XMLNode(node: Node): XMLValue {
    return when (node.nodeType) {
        Node.TEXT_NODE -> StringValue(node.textContent)
        else -> XMLNode(node.nodeName, attributes(node), nodes(node))
    }
}

private fun nodes(node: Node): List<XMLValue> {
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

fun XMLNode(xmlData: String): XMLNode {
    val document = parseXML(xmlData)
    return XMLNode(document)
}

data class XMLNode(val name: String, val attributes: Map<String, StringValue>, val nodes: List<XMLValue>) : XMLValue, ListValue {
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
    override fun exactMatchElseType(): XMLPattern {
        return XMLPattern(this)
    }

    override fun type(): Pattern {
        return XMLPattern()
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> {
        TODO("Not yet implemented")
    }

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> {
        TODO("Not yet implemented")
    }

    override fun listOf(valueList: List<Value>): Value {
        return XMLNode("", emptyMap(), valueList.map { it as XMLNode })
    }

    override fun toString(): String = toStringValue()
}