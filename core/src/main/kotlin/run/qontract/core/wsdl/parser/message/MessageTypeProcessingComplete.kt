package run.qontract.core.wsdl.parser.message

import run.qontract.core.wsdl.payload.SoapPayloadType

data class MessageTypeProcessingComplete(override val soapPayloadType: SoapPayloadType?) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        return this
    }
}