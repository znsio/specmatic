package run.qontract.core.wsdl

interface MessageTypeInfoParser {
    fun execute(): MessageTypeInfoParser
    val soapPayloadType: SoapPayloadType?
        get() {
            return null
        }
}