package run.qontract.core.wsdl

import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode

data class EmptySOAPPayload(private val soapMessageType: SOAPMessageType): SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = emptySoapMessage()
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}

fun emptySoapMessage(): XMLNode {
    val payload = soapSkeleton(emptyMap())
    val bodyNode = toXMLNode("<soapenv:Body/>")
    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
}

