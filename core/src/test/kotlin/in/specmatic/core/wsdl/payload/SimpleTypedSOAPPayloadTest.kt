package `in`.specmatic.core.wsdl.payload

import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.message.OCCURS_ATTRIBUTE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SimpleTypedSOAPPayloadTest {
    @Test
    fun `statement generation`() {
        val payload = SimpleTypedSOAPPayload(SOAPMessageType.Input, toXMLNode("<person/>"), emptyMap())
        val statement = payload.specmaticStatement().first().trim()
        assertThat(statement).isEqualTo("""And request-body
""${'"'}
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header $OCCURS_ATTRIBUTE_NAME="optional"/><soapenv:Body><person/></soapenv:Body></soapenv:Envelope>
""${'"'}""")
    }
}