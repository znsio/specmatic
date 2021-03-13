package run.qontract.core.wsdl.parser.message

import run.qontract.core.wsdl.payload.SoapPayloadType

interface MessageTypeInfoParser {
    fun execute(): MessageTypeInfoParser
    val soapPayloadType: SoapPayloadType?
        get() {
            return null
        }
}