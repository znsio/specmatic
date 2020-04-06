package run.qontract.core.value

import run.qontract.core.utilities.xmlToString
import org.w3c.dom.Node
import run.qontract.core.utilities.parseXML

data class XMLValue(val node: Node) : Value {
    constructor(xml: String): this(parseXML(xml).documentElement)

    override val httpContentType = "application/xml"

    override fun toDisplayValue(): String = toStringValue()
    override fun toStringValue() = xmlToString(node)

    override fun toString() = xmlToString(node)
    override fun equals(other: Any?) =
        when (other) {
            is XMLValue -> node.isEqualNode(other.node)
            else -> false
        }

    override fun hashCode(): Int = node.hashCode()
}