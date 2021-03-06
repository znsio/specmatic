package run.qontract.core.wsdl.payload

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import run.qontract.core.wsdl.parser.SOAPMessageType

internal class ComplexTypedSOAPPayloadTest {
    @Test
    fun `generates a complex payload with a single namespace`() {
        val type = ComplexTypedSOAPPayload(SOAPMessageType.Input, "person", "Person", mapOf("ns0" to "http://ns"))
        val statement = type.qontractStatement().first().trim()

        println(statement)
        assertThat(statement).isEqualTo("""And request-body
""${'"'}
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns0="http://ns" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header qontract_occurs="optional"/><soapenv:Body><person qontract_type="Person"/></soapenv:Body></soapenv:Envelope>
""${'"'}""")
    }
}