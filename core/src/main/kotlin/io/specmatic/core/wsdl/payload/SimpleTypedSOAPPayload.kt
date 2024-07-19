package io.specmatic.core.wsdl.payload

import io.specmatic.core.value.XMLNode
import io.specmatic.core.wsdl.parser.SOAPMessageType

data class SimpleTypedSOAPPayload(val soapMessageType: SOAPMessageType, val node: XMLNode, val namespaces: Map<String, String>) :
    SOAPPayload {
    override fun specmaticStatement(): List<String> {
        val body = soapMessage(node, namespaces)
        return listOf("And ${soapMessageType.specmaticBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}