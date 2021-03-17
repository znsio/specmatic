package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.wsdl.payload.SoapPayloadType

interface MessageTypeInfoParser {
    fun execute(): MessageTypeInfoParser
    val soapPayloadType: SoapPayloadType?
        get() {
            return null
        }
}