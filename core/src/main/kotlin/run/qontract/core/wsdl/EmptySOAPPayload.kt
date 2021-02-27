package run.qontract.core.wsdl

class EmptySOAPPayload(private val soapMessageType: SOAPMessageType): SOAPPayload {
    override fun qontractStatement(): List<String> {
        val body = emptySoapMessage()
        return listOf("And ${soapMessageType.qontractBodyType}-body\n\"\"\"\n$body\n\"\"\"")
    }
}