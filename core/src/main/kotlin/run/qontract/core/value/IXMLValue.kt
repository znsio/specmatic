package run.qontract.core.value

import org.w3c.dom.Document
import org.w3c.dom.Node

interface IXMLValue: Value {
    fun build(document: Document): Node
}