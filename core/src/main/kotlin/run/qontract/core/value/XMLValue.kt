package run.qontract.core.value

import run.qontract.core.utilities.xmlToString
import org.w3c.dom.Node
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.utilities.parseXML

//TODO Rewrite XML handling to eliminate duplication between XML and JSON
data class XMLValue(val node: Node) : Value {
    constructor(xml: String): this(parseXML(xml).documentElement)

    override val httpContentType = "application/xml"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = xmlToString(node)
    override fun displayableType(): String = "xml"
    override fun toExactType(): Pattern = XMLPattern(node)
    override fun type(): Pattern = XMLPattern("<empty/>")

    override fun typeDeclaration(typeName: String): TypeDeclaration {
        TODO("Not yet implemented")
    }

    override fun toString() = xmlToString(node)
    override fun equals(other: Any?) =
        when (other) {
            is XMLValue -> node.isEqualNode(other.node)
            else -> false
        }

    override fun hashCode(): Int = node.hashCode()
}