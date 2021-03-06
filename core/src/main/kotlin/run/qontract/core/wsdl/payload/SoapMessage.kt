package run.qontract.core.wsdl.payload

import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode


fun soapMessage(bodyPayload: XMLNode, namespaces: Map<String, String>): XMLNode {
    val payload = soapSkeleton(namespaces)
    val bodyNode = toXMLNode("<soapenv:Body/>").let {
        it.copy(childNodes = it.childNodes.plus(bodyPayload))
    }

    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
}
