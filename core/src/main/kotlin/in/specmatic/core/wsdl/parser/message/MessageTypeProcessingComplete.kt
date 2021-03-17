package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.wsdl.payload.SoapPayloadType

data class MessageTypeProcessingComplete(override val soapPayloadType: SoapPayloadType?) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        return this
    }
}