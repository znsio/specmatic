package run.qontract.core.wsdl

class EmptyHTTPBodyPayload() : SOAPPayload {
    override fun qontractStatement(): List<String> {
        return emptyList()
    }
}