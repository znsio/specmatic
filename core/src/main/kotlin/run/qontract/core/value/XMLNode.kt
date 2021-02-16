package run.qontract.core.value

import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Node
import run.qontract.core.ExampleDeclarations
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.utilities.newBuilder
import run.qontract.core.utilities.parseXML
import run.qontract.core.utilities.xmlToString

fun XMLNode(document: Document): XMLNode = nonTextXMLNode(document.documentElement)

fun XMLNode(node: Node, parentNamespaces: Map<String, String> = emptyMap()): XMLValue {
    return when (node.nodeType) {
        Node.TEXT_NODE -> StringValue(node.textContent)
        else -> nonTextXMLNode(node, parentNamespaces)
    }
}

private fun nonTextXMLNode(node: Node, parentNamespaces: Map<String, String> = emptyMap()): XMLNode {
    val attributes = attributes(node)
    val namespacesForChildrenToInherit = getNamespaces(attributes)
    return XMLNode(node.nodeName, attributes(node), childNodes(node, namespacesForChildrenToInherit), parentNamespaces)
}

private fun xmlNameWithoutNamespace(name: String): String =
        name.substringAfter(':')

private fun childNodes(node: Node, parentNamespaces: Map<String, String>): List<XMLValue> {
    return 0.until(node.childNodes.length).map {
        node.childNodes.item(it)
    }.fold(listOf()) { acc, item ->
        acc.plus(XMLNode(item, parentNamespaces))
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

fun getNamespace(realName: String): String {
    val parts = realName.split(":")
    return if(parts.size > 1)
        parts[0]
    else ""
}

fun getNamespaces(attributes: Map<String, StringValue>): Map<String, String> =
    attributes.filterKeys { it.startsWith("xmlns:") }.mapKeys { it.key.removePrefix("xmlns:") }.mapValues { it.value.toString() }

data class XMLNode(val name: String, val realName: String, val attributes: Map<String, StringValue>, val nodes: List<XMLValue>, val namespacePrefix: String, val namespaces: Map<String, String>) : XMLValue, ListValue {
    constructor(realName: String, attributes: Map<String, StringValue>, nodes: List<XMLValue>, parentNamespaces: Map<String, String> = emptyMap()) : this(xmlNameWithoutNamespace(realName), realName, attributes, nodes, getNamespace(realName), parentNamespaces.plus(getNamespaces(attributes)))

    val qname: String
        get() {
            val namespaceQualifier = when {
                namespacePrefix.isNotBlank() -> "{${resolvedNamespace()}}"
                attributes.containsKey("xmlns") -> "{${attributes["xmlns"]}}"
                else -> ""
            }

            return "$namespaceQualifier$name"
        }

    private fun resolvedNamespace(): String = namespaces[namespacePrefix]
        ?: throw ContractException("Namespace prefix $namespacePrefix cannot be resolved")

    override val httpContentType: String = "text/xml"

    override val list: List<Value>
        get() = nodes

    private fun build(): Document {
        val document = newBuilder().newDocument()

        val node = build(document)
        document.appendChild(node)

        return document
    }

    override fun build(document: Document): Node {
        val newElement = document.createElement(realName)

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
        return XMLNode("", "", emptyMap(), valueList.map { it as XMLNode }, "", emptyMap())
    }

    override fun toString(): String = toStringValue()

    fun findFirstChildByName(name: String): XMLNode? = nodes.filterIsInstance<XMLNode>().find { it.name == name }

    fun findFirstChildByPath(path: String): XMLNode? = findFirstChildByPath(path.split("."))

    private fun findFirstChildByPath(path: List<String>): XMLNode? = when {
        path.isEmpty() -> this
        else -> findFirstChildByPath(path.first(), path.drop(1))
    }

    private fun findFirstChildByPath(childName: String, rest: List<String>): XMLNode? =
        findFirstChildByName(childName)?.findFirstChildByPath(rest)

    fun findChildrenByName(name: String): List<XMLNode> = nodes.filterIsInstance<XMLNode>().filter { it.name == name }
}