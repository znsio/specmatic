package `in`.specmatic.conversions

import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import java.io.File
import java.net.URI

class WsdlKtTest {

    @Test
    fun `should create stub from gherkin that include wsdl`() {
        val wsdlContent = """
            <?xml version="1.0"?>
            <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                              xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                              xmlns:qr="http://specmatic.in/SOAPService/"
                              targetNamespace="http://specmatic.in/SOAPService/">
                <wsdl:types>
                    <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                                targetNamespace="http://specmatic.in/SOAPService/">
                        <xsd:element name="SimpleRequest" type="xsd:string"/>
                        <xsd:element name="SimpleResponse" type="xsd:string"/>
                    </xsd:schema>
                </wsdl:types>

                <wsdl:message name="simpleInputMessage">
                    <wsdl:part name="simpleInputPart" element="qr:SimpleRequest"/>
                </wsdl:message>
                <wsdl:message name="simpleOutputMessage">
                    <wsdl:part name="simpleOutputPart" element="qr:SimpleResponse"/>
                </wsdl:message>

                <wsdl:portType name="simplePortType">
                    <wsdl:operation name="SimpleOperation">
                        <wsdl:input name="simpleInput"
                                    message="qr:simpleInputMessage"/>
                        <wsdl:output name="simpleOutput"
                                     message="qr:simpleOutputMessage"/>
                    </wsdl:operation>
                </wsdl:portType>

                <wsdl:binding name="simpleBinding" type="qr:simplePortType">
                    <soap:binding transport="http://schemas.xmlsoap.org/soap/http"/>
                    <wsdl:operation name="SimpleOperation">
                        <soap:operation
                                soapAction="http://specmatic.in/SOAPService/SimpleOperation"/>
                        <wsdl:input name="simpleInput">
                            <soap:body use="literal"/>
                        </wsdl:input>
                        <wsdl:output name="simpleOutput">
                            <soap:body use="literal"/>
                        </wsdl:output>
                    </wsdl:operation>
                </wsdl:binding>

                <wsdl:service name="simpleService">
                    <wsdl:port name="simplePort" binding="qr:simpleBinding">
                        <soap:address
                                location="http://specmatic.in/SOAPService/SimpleSOAP"/>
                    </wsdl:port>
                </wsdl:service>

            </wsdl:definitions>
        """.trimIndent()

        val wsdlFile = File("test.wsdl")
        wsdlFile.createNewFile()
        wsdlFile.writeText(wsdlContent)

        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test.wsdl           
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        HttpStub(wsdlFeature).use { stub ->
            val soapRequest =
                """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>WKTGM</SimpleRequest></soapenv:Body></soapenv:Envelope>"""

            val headers = HttpHeaders()
            headers.add("SOAPAction", """"http://specmatic.in/SOAPService/SimpleOperation"""")
            val request = HttpEntity(soapRequest, headers)
            val response = RestTemplate().exchange(
                URI.create("http://localhost:9000/SOAPService/SimpleSOAP"),
                HttpMethod.POST,
                request,
                String::class.java
            )
            Assertions.assertThat(response.statusCodeValue).isEqualTo(200)
            Assertions.assertThat(response.body)
                .matches("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>[A-Z]*</SimpleResponse></soapenv:Body></soapenv:Envelope>""")
            File("test.wsdl").delete()
        }
    }
}