package run.qontract.core.value

import run.qontract.core.utilities.xmlToString
import org.w3c.dom.Element
import org.w3c.dom.Node
import run.qontract.core.utilities.parseXML

data class XMLValue(val node: Node) : Value {
    constructor(xml: String): this(parseXML(xml).documentElement)
    override val value: Any = node
    override val httpContentType = "application/xml"
    override fun toStringValue() = xmlToString(node)
    override fun toString() = xmlToString(node)
    override fun equals(other: Any?) = if(other is XMLValue) { equals(other) } else false

    fun equals(other: XMLValue) = node.isEqualNode(other.node)

    override fun hashCode(): Int = node.hashCode()
}