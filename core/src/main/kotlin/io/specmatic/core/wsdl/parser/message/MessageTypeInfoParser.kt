package io.specmatic.core.wsdl.parser.message

import io.specmatic.core.wsdl.payload.SoapPayloadType

interface MessageTypeInfoParser {
    fun execute(): MessageTypeInfoParser
    val soapPayloadType: SoapPayloadType?
        get() {
            return null
        }
}