package run.qontract.core.wsdl

class MessageTypeProcessingComplete(override val soapPayloadType: SoapPayloadType?) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        return this
    }
}