package run.qontract.core.wsdl.parser.message

import run.qontract.core.wsdl.payload.SoapPayloadType

class MessageTypeProcessingComplete(override val soapPayloadType: SoapPayloadType?) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        return this
    }
}