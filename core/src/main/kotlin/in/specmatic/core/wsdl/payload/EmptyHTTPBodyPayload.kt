package `in`.specmatic.core.wsdl.payload

class EmptyHTTPBodyPayload : SOAPPayload {
    override fun specmaticStatement(): List<String> {
        return emptyList()
    }
}