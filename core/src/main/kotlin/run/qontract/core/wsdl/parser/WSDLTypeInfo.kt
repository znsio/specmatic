package run.qontract.core.wsdl.parser

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.StringValue
import run.qontract.core.value.XMLNode
import run.qontract.core.value.XMLValue

data class WSDLTypeInfo(val nodes: List<XMLValue> = emptyList(), val types: Map<String, XMLPattern> = emptyMap(), val namespacePrefixes: Set<String> = emptySet()) {
    fun getNamespaces(wsdlDefinitionNodeAttributes: Map<String, StringValue>): Map<String, String> {
        return namespacePrefixes.toList().map {
            Pair(it, wsdlDefinitionNodeAttributes.getValue("xmlns:$it").toStringValue())
        }.toMap()
    }

    val nodeTypeInfo: XMLNode
        get() {
            return XMLNode(TYPE_NODE_NAME, emptyMap(), nodes)
        }
}