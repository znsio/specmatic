package run.qontract.core.wsdl

import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode

interface SOAPPayload {
    fun qontractStatement(): List<String>
}

private fun soapSkeleton(namespaces: Map<String, String>): XMLNode {
    val namespacesString = when(namespaces.size){
        0 -> ""
        else -> namespaces.entries
            .joinToString(" ") {
                "xmlns:${it.key}=\"${it.value}\""
            }
            .prependIndent(" ")
    }
    return toXMLNode(
        """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="$primitiveNamespace"$namespacesString><soapenv:Header $OCCURS_ATTRIBUTE_NAME="$OPTIONAL_ATTRIBUTE_VALUE"/>
            </soapenv:Envelope>
        """)
}

fun emptySoapMessage(): XMLNode {
    val payload = soapSkeleton(emptyMap())
    val bodyNode = toXMLNode("<soapenv:Body/>")
    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
}

fun soapMessage(bodyPayload: XMLNode, namespaces: Map<String, String>): XMLNode {
    val payload = soapSkeleton(namespaces)
    val bodyNode = toXMLNode("<soapenv:Body/>").let {
        it.copy(childNodes = it.childNodes.plus(bodyPayload))
    }

    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
}
