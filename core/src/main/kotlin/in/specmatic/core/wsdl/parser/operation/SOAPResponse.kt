package `in`.specmatic.core.wsdl.parser.operation

import `in`.specmatic.core.wsdl.payload.SOAPPayload

data class SOAPResponse(val responsePayload: SOAPPayload) {
    fun statements(): List<String> {
        val statusStatement = listOf("Then status 200")
        val responseBodyStatement = responsePayload.qontractStatement()
        return statusStatement.plus(responseBodyStatement)
    }
}