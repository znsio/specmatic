package run.qontract.core.wsdl

import run.qontract.core.pattern.Pattern
import run.qontract.core.value.XMLValue

data class WSDLTypeInfo(val nodes: List<XMLValue> = emptyList(), val types: Map<String, Pattern> = emptyMap(), val namespacesPrefixes: Set<String> = emptySet())