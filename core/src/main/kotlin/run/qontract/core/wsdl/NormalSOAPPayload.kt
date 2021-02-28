package run.qontract.core.wsdl

import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode

data class NormalSOAPPayload(val soapMessageType: SOAPMessageType, val nodeName: String, val qontractTypeName: String, val namespaces: Map<String, String>) :
    SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = soapMessage(toXMLNode("<$nodeName qontract_type=\"$qontractTypeName\"/>"), namespaces)
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}

fun soapMessage(bodyPayload: XMLNode, namespaces: Map<String, String>): XMLNode {
    val payload = soapSkeleton(namespaces)
    val bodyNode = toXMLNode("<soapenv:Body/>").let {
        it.copy(childNodes = it.childNodes.plus(bodyPayload))
    }

    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
}
