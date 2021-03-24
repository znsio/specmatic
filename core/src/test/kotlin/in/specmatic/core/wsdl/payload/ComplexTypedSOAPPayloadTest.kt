package `in`.specmatic.core.wsdl.payload

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.TYPE_ATTRIBUTE_NAME
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.message.OCCURS_ATTRIBUTE_NAME

internal class ComplexTypedSOAPPayloadTest {
    @Test
    fun `generates a complex payload with a single namespace`() {
        val type = ComplexTypedSOAPPayload(SOAPMessageType.Input, "person", "Person", mapOf("ns0" to "http://ns"))
        val statement = type.qontractStatement().first().trim()

        println(statement)
        assertThat(statement).isEqualTo("""And request-body
""${'"'}
<soapenv:Envelope xmlns:ns0="http://ns" xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header $OCCURS_ATTRIBUTE_NAME="optional"/><soapenv:Body><person $TYPE_ATTRIBUTE_NAME="Person"/></soapenv:Body></soapenv:Envelope>
""${'"'}""")
    }
}