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
import run.qontract.core.utilities.xmlToPrettyString
import run.qontract.core.utilities.xmlToString

fun toXMLNode(document: Document): XMLNode = nonTextXMLNode(document.documentElement)

fun toXMLNode(node: Node, parentNamespaces: Map<String, String> = emptyMap()): XMLValue {
    return when (node.nodeType) {
        Node.TEXT_NODE -> StringValue(node.textContent)
        else -> nonTextXMLNode(node, parentNamespaces)
    }
}

private fun nonTextXMLNode(node: Node, parentNamespaces: Map<String, String> = emptyMap()): XMLNode {
    val attributes = attributes(node)
    val namespacesForChildrenToInherit = getNamespaces(attributes)
    return XMLNode(node.nodeName, attributes(node), childNodes(node, parentNamespaces.plus(namespacesForChildrenToInherit)), parentNamespaces)
}

private fun childNodes(node: Node, parentNamespaces: Map<String, String>): List<XMLValue> {
    return 0.until(node.childNodes.length).map {
        node.childNodes.item(it)
    }.fold(listOf()) { acc, item ->
        acc.plus(toXMLNode(item, parentNamespaces))
    }
}

private fun attributes(node: Node): Map<String, StringValue> {
    return 0.until(node.attributes.length).map {
        node.attributes.item(it) as Attr
    }.fold(mapOf()) { acc, item ->
        acc.plus(item.name to StringValue(item.value))
    }
}

fun toXMLNode(xmlData: String): XMLNode {
    val document = parseXML(xmlData)
    return toXMLNode(document)
}

fun String.withoutNamespacePrefix(): String = this.substringAfter(':')

fun String.namespacePrefix(): String {
    val parts = this.split(":")
    return when (parts.size) {
        1 -> ""
        else -> parts.first()
    }
}

fun getNamespaces(attributes: Map<String, StringValue>): Map<String, String> =
    attributes.filterKeys { it.startsWith("xmlns:") }.mapKeys { it.key.removePrefix("xmlns:") }.mapValues { it.value.toString() }

data class XMLNode(val name: String, val realName: String, val attributes: Map<String, StringValue>, val childNodes: List<XMLValue>, val namespacePrefix: String, val namespaces: Map<String, String>) : XMLValue, ListValue {
    constructor(realName: String, attributes: Map<String, StringValue>, childNodes: List<XMLValue>, parentNamespaces: Map<String, String> = emptyMap()) : this(realName.withoutNamespacePrefix(), realName, attributes, childNodes, realName.namespacePrefix(), parentNamespaces.plus(getNamespaces(attributes)))

    fun createNewNode(realName: String, attributes: Map<String, String> = emptyMap()): XMLNode {
        val namespace = realName.namespacePrefix()

        if(namespace.isNotBlank() && !namespaces.containsKey(namespace))
            throw ContractException("Namespace prefix $namespace not found, can't create a node by the name $realName")

        return XMLNode(realName, attributes.mapValues { StringValue(it.value) }, emptyList(), namespaces)
    }

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
        get() = childNodes

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

        val newNodes = childNodes.map {
            it.build(document)
        }

        for(node in newNodes) {
            newElement.appendChild(node)
        }

        return newElement
    }

    override fun displayableValue(): String = toStringValue()

    override fun toStringValue(): String = xmlToString(build())

    fun toPrettyStringValue(): String = xmlToPrettyString(build())

    override fun displayableType(): String = "xml"
    override fun exactMatchElseType(): XMLPattern {
        return XMLPattern(this)
    }

    override fun type(): Pattern {
        return XMLPattern()
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> {
        return typeDeclarationWithKey(exampleKey, types, exampleDeclarations)
    }

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> {
        val newTypeName = exampleDeclarations.getNewName(key.capitalize(), types.keys)

        val typeDeclaration = TypeDeclaration("($newTypeName)", types.plus(newTypeName to XMLPattern(this, key)))

        return Pair(typeDeclaration, exampleDeclarations)
    }

    override fun listOf(valueList: List<Value>): Value {
        return XMLNode("", "", emptyMap(), valueList.map { it as XMLNode }, "", emptyMap())
    }

    override fun toString(): String = toStringValue()

    fun findFirstChildByName(name: String): XMLNode? =
        childNodes.filterIsInstance<XMLNode>().find { it.name == name }

    fun findFirstChildByPath(path: String): XMLNode? =
        findFirstChildByPath(path.split("."))

    private fun findFirstChildByPath(path: List<String>): XMLNode? = when {
        path.isEmpty() -> this
        else -> findFirstChildByPath(path.first(), path.drop(1))
    }

    private fun findFirstChildByPath(childName: String, rest: List<String>): XMLNode? =
        findFirstChildByName(childName)?.findFirstChildByPath(rest)

    fun findChildrenByName(name: String): List<XMLNode> = childNodes.filterIsInstance<XMLNode>().filter { it.name == name }

    fun resolveNamespace(name: String): String {
        val namespacePrefix = name.namespacePrefix()

        return when {
            namespacePrefix.isBlank() -> ""
            else -> namespaces[name.namespacePrefix()] ?: throw ContractException("Namespace ${name.namespacePrefix()} not found in node $this\nAvailable namespaces: $namespaces")
        }
    }

    fun getAttributeValue(path: String, attributeName: String): String {
        val childNode = getXMLNodeByPath(path)
        return childNode.attributes[attributeName]?.toStringValue() ?: throw ContractException("Couldn't find attribute $attributeName at path $path")
    }

    fun getAttributeValue(attributeName: String): String {
        return this.attributes[attributeName]?.toStringValue() ?: throw ContractException("Couldn't find attribute $attributeName in node $this")
    }

    fun getXMLNodeByPath(path: String): XMLNode =
        this.findFirstChildByPath(path) ?: throw ContractException("Couldn't find node at path $path")

    fun getXMLNodeOrNull(path: String): XMLNode? =
        this.findFirstChildByPath(path)

    fun getXMLNodeByName(name: String): XMLNode =
        this.findFirstChildByName(name) ?: throw ContractException("Couldn't find node named $name")

    fun getXMLNodeByAttributeValue(attributeName: String, typeName: String): XMLNode {
        return this.childNodes.filterIsInstance<XMLNode>().find {
            it.attributes[attributeName]?.toStringValue() == typeName
        } ?: throw ContractException("Couldn't find a node with attribute $attributeName=$typeName")
    }

    fun findByNodeNameAndAttribute(nodeName: String, attributeName: String, typeName: String): XMLNode {
        return this.childNodes.filterIsInstance<XMLNode>().find {
            it.name == nodeName && it.attributes[attributeName]?.toStringValue() == typeName
        } ?: throw ContractException("Couldn't find a node with attribute $attributeName=$typeName")
    }

    fun firstNode(): XMLNode? =
        this.childNodes.filterIsInstance<XMLNode>().firstOrNull()

    fun findNodeByNameAttribute(valueOfNameAttribute: String): XMLNode {
        return this.childNodes.filterIsInstance<XMLNode>().find {
            it.attributes["name"]?.toStringValue() == valueOfNameAttribute
        } ?: throw ContractException("Couldn't find name attribute")
    }
}
