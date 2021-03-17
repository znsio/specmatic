package run.qontract.core.wsdl.payload

import run.qontract.core.pattern.TYPE_ATTRIBUTE_NAME
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.SOAPMessageType

data class ComplexTypedSOAPPayload(val soapMessageType: SOAPMessageType, val nodeName: String, val qontractTypeName: String, val namespaces: Map<String, String>) :
    SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = soapMessage(toXMLNode("<$nodeName $TYPE_ATTRIBUTE_NAME=\"$qontractTypeName\"/>"), namespaces)
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}
