package `in`.specmatic.core.wsdl.parser

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.XMLValue

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