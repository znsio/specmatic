package run.qontract.core.wsdl

import run.qontract.core.pattern.Pattern

data class SoapPayloadTypeInfo(val typeName: String, val types: Map<String, Pattern>, val namespacesPrefixes: Set<String>)