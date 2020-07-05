package run.qontract.core.value

import org.w3c.dom.Document
import org.w3c.dom.Node

interface XMLValue: Value {
    fun build(document: Document): Node
}