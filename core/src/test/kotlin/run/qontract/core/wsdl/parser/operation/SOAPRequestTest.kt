package run.qontract.core.wsdl.parser.operation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.payload.EmptySOAPPayload

internal class SOAPRequestTest {
    @Test
    fun `generates gherkin statements for a request with a soap action`() {
        val soapRequest = SOAPRequest("/customer", "add", "/add", EmptySOAPPayload(SOAPMessageType.Input))

        val statements = soapRequest.statements()
        assertThat(statements).hasSize(3)

        assertThat(statements).contains("When POST /customer")
        assertThat(statements).contains("And request-header SOAPAction \"/add\"")
        assertThat(statements).contains("And request-body\n" +
                "\"\"\"\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><soapenv:Header qontract_occurs=\"optional\"/><soapenv:Body/></soapenv:Envelope>\n" +
                "\"\"\"")

    }

    @Test
    fun `generates gherkin statements for a request without a soap action`() {
        val soapRequest = SOAPRequest("/customer", "add", "", EmptySOAPPayload(SOAPMessageType.Input))

        val statements = soapRequest.statements()
        assertThat(statements).hasSize(2)

        assertThat(statements).contains("When POST /customer")
        assertThat(statements).contains("And request-body\n" +
                "\"\"\"\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><soapenv:Header qontract_occurs=\"optional\"/><soapenv:Body/></soapenv:Envelope>\n" +
                "\"\"\"")

    }
}