package `in`.specmatic.core.wsdl.payload

import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType

data class EmptySOAPPayload(private val soapMessageType: SOAPMessageType): SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = emptySoapMessage()
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}

internal fun emptySoapMessage(): XMLNode {
    val payload = soapSkeleton(emptyMap())
    val bodyNode = toXMLNode("<soapenv:Body/>")
    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
}

