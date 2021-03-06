package run.qontract.core.wsdl.payload

import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.OCCURS_ATTRIBUTE_NAME
import run.qontract.core.wsdl.parser.OPTIONAL_ATTRIBUTE_VALUE
import run.qontract.core.wsdl.parser.primitiveNamespace

interface SOAPPayload {
    fun qontractStatement(): List<String>
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
