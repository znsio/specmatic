package run.qontract.core.wsdl.parser

import run.qontract.core.wsdl.payload.SoapPayloadType

class MessageTypeProcessingComplete(override val soapPayloadType: SoapPayloadType?) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        return this
    }
}