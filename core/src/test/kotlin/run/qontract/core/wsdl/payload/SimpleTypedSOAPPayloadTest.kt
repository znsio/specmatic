package run.qontract.core.wsdl.payload

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.message.OCCURS_ATTRIBUTE_NAME

internal class SimpleTypedSOAPPayloadTest {
    @Test
    fun temp() {
        val payload = SimpleTypedSOAPPayload(SOAPMessageType.Input, toXMLNode("<person/>"), emptyMap())
        val statement = payload.qontractStatement().first().trim()
        assertThat(statement).isEqualTo("""And request-body
""${'"'}
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header $OCCURS_ATTRIBUTE_NAME="optional"/><soapenv:Body><person/></soapenv:Body></soapenv:Envelope>
""${'"'}""")
    }
}