package run.qontract.core.wsdl.parser

import run.qontract.core.wsdl.payload.SoapPayloadType

interface MessageTypeInfoParser {
    fun execute(): MessageTypeInfoParser
    val soapPayloadType: SoapPayloadType?
        get() {
            return null
        }
}