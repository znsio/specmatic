package `in`.specmatic.core.wsdl.payload

import `in`.specmatic.core.pattern.TYPE_ATTRIBUTE_NAME
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType

data class ComplexTypedSOAPPayload(val soapMessageType: SOAPMessageType, val nodeName: String, val qontractTypeName: String, val namespaces: Map<String, String>) :
    SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = soapMessage(toXMLNode("<$nodeName $TYPE_ATTRIBUTE_NAME=\"$qontractTypeName\"/>"), namespaces)
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n${body.toPrettyStringValue()}\n\"\"\"")
    }
}
