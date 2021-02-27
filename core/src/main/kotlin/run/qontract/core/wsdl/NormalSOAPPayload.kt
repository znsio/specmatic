package run.qontract.core.wsdl

import run.qontract.core.value.toXMLNode

data class NormalSOAPPayload(val soapMessageType: SOAPMessageType, val qontractTypeName: String, val namespaces: Map<String, String>) :
    SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = soapMessage(toXMLNode("<$XML_TYPE_PREFIX$qontractTypeName/>"), namespaces)
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}