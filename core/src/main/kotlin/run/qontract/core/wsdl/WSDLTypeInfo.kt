package run.qontract.core.wsdl

import run.qontract.core.pattern.Pattern
import run.qontract.core.value.StringValue
import run.qontract.core.value.XMLValue

data class WSDLTypeInfo(val nodes: List<XMLValue> = emptyList(), val types: Map<String, Pattern> = emptyMap(), val namespacesPrefixes: Set<String> = emptySet()) {
    fun getNamespaces(wsdlDefinitionNodeAttributes: Map<String, StringValue>): Map<String, String> {
        return namespacesPrefixes.toList().map {
            Pair(it, wsdlDefinitionNodeAttributes.getValue("xmlns:$it").toStringValue())
        }.toMap()
    }
}