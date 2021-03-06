package run.qontract.core.wsdl.payload

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.wsdl.parser.SOAPMessageType

internal class EmptySOAPPayloadTest {
    @Test
    fun `generates a request-body statement with an empty SOAP body`() {
        val statement = EmptySOAPPayload(SOAPMessageType.Input).qontractStatement().first().trim()
        assertThat(statement).isEqualTo("""And request-body
""${'"'}
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header qontract_occurs="optional"/><soapenv:Body/></soapenv:Envelope>
""${'"'}""")
    }
}