package io.specmatic.core.wsdl.payload

import io.specmatic.core.value.XMLNode
import io.specmatic.core.value.toXMLNode
import io.specmatic.core.wsdl.parser.message.OCCURS_ATTRIBUTE_NAME
import io.specmatic.core.wsdl.parser.message.OPTIONAL_ATTRIBUTE_VALUE
import io.specmatic.core.wsdl.parser.message.primitiveNamespace


fun soapMessage(bodyPayload: XMLNode, namespaces: Map<String, String>): XMLNode {
    val payload = soapSkeleton(namespaces)
    val bodyNode = toXMLNode("<soapenv:Body/>").let {
        it.copy(childNodes = it.childNodes.plus(bodyPayload))
    }

    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
}

internal fun soapSkeleton(namespaces: Map<String, String>): XMLNode {
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
