package run.qontract.core.wsdl.payload

import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.SOAPMessageType

data class SimpleTypedSOAPPayload(val soapMessageType: SOAPMessageType, val node: XMLNode, val namespaces: Map<String, String>) :
    SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = soapMessage(node, namespaces)
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}