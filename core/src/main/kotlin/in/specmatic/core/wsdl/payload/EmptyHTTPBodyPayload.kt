package `in`.specmatic.core.wsdl.payload

class EmptyHTTPBodyPayload() : SOAPPayload {
    override fun qontractStatement(): List<String> {
        return emptyList()
    }
}