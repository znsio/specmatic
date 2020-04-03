package run.qontract.core.value

import run.qontract.core.utilities.xmlToString
import org.w3c.dom.Document

data class XMLValue(private val document: Document) : Value {
    override val value: Any = document
    override val httpContentType = "application/xml"
    override fun toStringValue() = xmlToString(document)
    override fun toString() = xmlToString(document)
    override fun equals(other: Any?) = if(other is XMLValue) { equals(other) } else false

    fun equals(other: XMLValue) = document.documentElement.isEqualNode(other.document.documentElement)

    override fun hashCode(): Int = document.hashCode()
}