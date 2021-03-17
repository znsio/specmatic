package run.qontract.core.wsdl.payload

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.message.OCCURS_ATTRIBUTE_NAME

internal class SoapMessageKtTest {
    @Test
    fun `generate a soap message with no namespaces`() {
        val message = soapMessage(toXMLNode("<person/>"), emptyMap()).toStringValue()
        assertThat(message).isEqualTo("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header $OCCURS_ATTRIBUTE_NAME="optional"/><soapenv:Body><person/></soapenv:Body></soapenv:Envelope>""")
    }

    @Test
    fun `generate a soap message with one namespace`() {
        val message = soapMessage(toXMLNode("<person/>"), mapOf("ns" to "http://namespace.com")).toStringValue()
        println(message)
        assertThat(message).isEqualTo("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns="http://namespace.com" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header $OCCURS_ATTRIBUTE_NAME="optional"/><soapenv:Body><person/></soapenv:Body></soapenv:Envelope>""")
    }
}